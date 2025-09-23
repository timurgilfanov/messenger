package timur.gilfanov.messenger.debug

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.data.source.local.LocalGetSettingsError
import timur.gilfanov.messenger.data.source.local.LocalUpdateSettingsError
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.util.Logger

@Suppress("TooManyFunctions", "LongParameterList") // Debug code
@Singleton
class DebugDataRepositoryImpl @Inject constructor(
    private val localDebugDataSource: LocalDebugDataSource,
    private val remoteDebugDataSource: RemoteDebugDataSource,
    private val sampleDataProvider: SampleDataProvider,
    @param:Named("debug") private val coroutineScope: CoroutineScope,
    private val logger: Logger,
) : DebugDataRepository {
    companion object {
        private const val TAG = "DebugDataRepository"
        private const val AUTO_ACTIVITY_MIN_DELAY_MS = 5000L
        private const val AUTO_ACTIVITY_MAX_DELAY_MS = 15000L
    }

    private var autoActivityJob: Job? = null

    /**
     * Flow of current debug settings
     */
    override val settings: Flow<DebugSettings> = localDebugDataSource.settings

    init {
        // Monitor auto-activity setting and start/stop simulation accordingly
        settings
            .map { it.autoActivity }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) startAutoActivity() else stopAutoActivity()
            }
            .launchIn(coroutineScope)
    }

    /**
     * Initialize debug data with the specified scenario
     */
    override suspend fun initializeWithScenario(
        scenario: DataScenario,
    ): ResultWithError<Unit, RegenerateDataError> {
        logger.d(TAG, "Initializing with scenario: ${scenario.name}")
        return regenerateData(scenario)
    }

    /**
     * Regenerate data using current scenario
     */
    override suspend fun regenerateData(): ResultWithError<Unit, RegenerateDataError> {
        val currentSettings = settings.first()
        return regenerateData(currentSettings.scenario)
    }

    /**
     * Regenerate data using specified scenario
     */
    @Suppress("ReturnCount") // Early returns for error handling
    private suspend fun regenerateData(
        scenario: DataScenario,
    ): ResultWithError<Unit, RegenerateDataError> {
        logger.d(TAG, "Regenerating data for scenario: ${scenario.name}")

        clearData().let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Data clearing encountered issues: ${result.error}")
                return ResultWithError.Failure(RegenerateDataError(clearData = result.error))
            }
        }

        val chats = generateData(scenario).let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Data generation failed: ${result.error}")
                return ResultWithError.Failure(RegenerateDataError(generateData = result.error))
            }
            (result as ResultWithError.Success).data
        }

        populateRemoteDataSource(chats).let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Populating remote data source failed: ${result.error}")
                return ResultWithError.Failure(RegenerateDataError(populateRemote = result.error))
            }
        }

        localDebugDataSource.updateSettings { settings ->
            settings.copy(
                scenario = scenario,
                lastGenerationTimestamp = Clock.System.now(),
            )
        }.let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Updating settings with new scenario failed: ${result.error}")
                return ResultWithError.Failure(
                    RegenerateDataError(updateSettings = result.error.toRepositoryError()),
                )
            }
        }

        logger.d(TAG, "Data generation complete - sync will refresh UI with new data")
        return ResultWithError.Success(Unit)
    }

    private fun LocalUpdateSettingsError.toRepositoryError(): UpdateSettingsError = when (this) {
        is LocalUpdateSettingsError.TransformError -> UpdateSettingsError.TransformError(e)
        is LocalUpdateSettingsError.WriteError -> UpdateSettingsError.WriteError(e)
    }

    private fun LocalGetSettingsError.toRepositoryError(): GetSettingsError = when (this) {
        is LocalGetSettingsError.ReadError -> GetSettingsError.ReadError
        is LocalGetSettingsError.NoData -> GetSettingsError.NoData
    }

    @Suppress("TooGenericExceptionCaught") // TODO handle errors in remote data source
    private fun populateRemoteDataSource(
        chats: List<Chat>,
    ): ResultWithError<Unit, PopulateRemoteError> = try {
        chats.forEach { chat ->
            val fixedChat = fixMessageRecipients(chat)
            remoteDebugDataSource.addChat(fixedChat)
        }
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        ResultWithError.Failure(PopulateRemoteError(reason = e))
    }

    private fun generateData(
        scenario: DataScenario,
    ): ResultWithError<List<Chat>, GenerateDataError> {
        val config = scenario.toConfig()
        val chats = sampleDataProvider.generateChats(config)
        return if (chats.isEmpty() && scenario != DataScenario.EMPTY) {
            ResultWithError.Failure(GenerateDataError.NoChatsGenerated)
        } else {
            ResultWithError.Success(chats)
        }
    }

    /**
     * Clear all data
     */
    override suspend fun clearData(): ResultWithError<Unit, ClearDataError> {
        logger.d(TAG, "Clearing all debug data")

        logger.d(TAG, "Clearing local database cache")
        var operationsCounter = 0
        val failedLocalOperations = mutableListOf<Pair<String, LocalDataSourceError>>()

        operationsCounter++
        localDebugDataSource.deleteAllChats().let { res ->
            if (res is ResultWithError.Failure) {
                logger.w(TAG, "Failed to delete chats: ${res.error}")
                failedLocalOperations.add("deleteAllChats" to res.error)
            }
        }

        operationsCounter++
        localDebugDataSource.deleteAllMessages().let { res ->
            if (res is ResultWithError.Failure) {
                logger.w(TAG, "Failed to delete messages: ${res.error}")
                failedLocalOperations.add("deleteAllMessages" to res.error)
            }
        }

        // Not critical, but try to clear sync timestamp as well
        operationsCounter++
        localDebugDataSource.clearSyncTimestamp().let { res ->
            if (res is ResultWithError.Failure) {
                logger.w(TAG, "Failed to clear sync timestamp: ${res.error}")
                failedLocalOperations.add("clearSyncTimestamp" to res.error)
            }
        }

        val failedRemoteOperations = mutableListOf<Pair<String, Exception>>()
        // Clear remote server data (should not fail in fake impl, but guard anyway)
        operationsCounter++
        @Suppress("TooGenericExceptionCaught") // TODO catch in remote data source
        try {
            remoteDebugDataSource.clearData()
        } catch (e: Exception) {
            logger.w(TAG, "Failed to clear remote data: ${e.message}")
            failedRemoteOperations.add("clearRemoteData" to e)
        }

        val failedOperations =
            failedLocalOperations.map { it.first to it.second.toString() } +
                failedRemoteOperations.map { it.first to it.second.toString() }
        if (failedOperations.isNotEmpty()) {
            return ResultWithError.Failure(
                ClearDataError(
                    partialSuccess = failedOperations.size != operationsCounter,
                    failedOperations = failedOperations,
                ),
            )
        }
        return ResultWithError.Success(Unit)
    }

    /**
     * Update debug settings
     */
    override suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, UpdateSettingsError> =
        when (val result = localDebugDataSource.updateSettings(transform)) {
            is ResultWithError.Success -> ResultWithError.Success(result.data)
            is ResultWithError.Failure -> ResultWithError.Failure(result.error.toRepositoryError())
        }

    private fun startAutoActivity() {
        stopAutoActivity() // Stop any existing activity

        logger.d(TAG, "Starting auto-activity simulation")
        autoActivityJob = coroutineScope.launch {
            while (isActive) {
                try {
                    // Random delay between 5-15 seconds
                    delay(Random.nextLong(AUTO_ACTIVITY_MIN_DELAY_MS, AUTO_ACTIVITY_MAX_DELAY_MS))
                    simulateNewMessage()
                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: IllegalStateException) {
                    logger.w(TAG, "Error in auto-activity simulation - illegal state", e)
                } catch (e: IllegalArgumentException) {
                    logger.w(TAG, "Error in auto-activity simulation - invalid arguments", e)
                }
            }
        }
    }

    private fun stopAutoActivity() {
        autoActivityJob?.cancel()
        autoActivityJob = null
        logger.d(TAG, "Stopped auto-activity simulation")
    }

    private fun simulateNewMessage() {
        try {
            // Get a random existing chat and add a message to it
            val currentChats = remoteDebugDataSource.getChats()
            if (currentChats.isNotEmpty()) {
                val randomChat = currentChats.random()
                val newMessage = sampleDataProvider.generateSingleMessage(randomChat)

                logger.d(TAG, "Simulating new message in chat: ${randomChat.name}")
                remoteDebugDataSource.addMessage(newMessage)
            }
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: IllegalStateException) {
            logger.w(TAG, "Failed to simulate new message - illegal state", e)
        } catch (e: IllegalArgumentException) {
            logger.w(TAG, "Failed to simulate new message - invalid arguments", e)
        }
    }

    /**
     * Fix message recipient IDs to match the chat ID
     * This is necessary because SampleDataProvider generates random IDs for recipients
     */
    private fun fixMessageRecipients(chat: Chat): Chat {
        val fixedMessages = chat.messages.map { message ->
            when (message) {
                is TextMessage -> {
                    message.copy(recipient = chat.id)
                }
                // Add other message types here if needed
                else -> message
            }
        }

        return chat.copy(messages = fixedMessages.toPersistentList())
    }
}
