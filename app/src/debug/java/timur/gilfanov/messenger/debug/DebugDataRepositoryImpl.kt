package timur.gilfanov.messenger.debug

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
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
import timur.gilfanov.messenger.data.source.remote.AddChatError
import timur.gilfanov.messenger.data.source.remote.ClearDataError as RemoteDataSourceClearDataError
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
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
        const val AUTO_ACTIVITY_MAX_DELAY_MS = 15000L
    }

    private var autoActivityJob: Job? = null

    /**
     * Flow of current debug settings
     */
    override val settings: Flow<ResultWithError<DebugSettings, GetSettingsError.ReadError>> =
        localDebugDataSource.settings.map {
            when (it) {
                is ResultWithError.Success -> ResultWithError.Success(it.data)
                is ResultWithError.Failure -> ResultWithError.Failure(
                    when (it.error) {
                        is LocalGetSettingsError.ReadError -> GetSettingsError.ReadError
                    },
                )
            }
        }

    init {
        // Monitor auto-activity setting and start/stop simulation accordingly
        settings
            .filterIsInstance<ResultWithError.Success<DebugSettings, LocalGetSettingsError>>()
            .map { it.data.autoActivity }
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
        val currentSettings = getSettings().let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Failed to fetch current settings: ${result.error}")
                return ResultWithError.Failure(RegenerateDataError(getSettings = result.error))
            }
            (result as ResultWithError.Success).data
        }
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

    private fun populateRemoteDataSource(
        chats: List<Chat>,
    ): ResultWithError<Unit, PopulateRemoteError> {
        val failures = mutableMapOf<ChatId, AddChatError>()
        chats.forEach { chat ->
            val fixedChat = fixMessageRecipients(chat)
            remoteDebugDataSource.addChat(fixedChat).let { result ->
                if (result is ResultWithError.Failure) {
                    logger.w(TAG, "Failed to add chat '${chat.name}': ${result.error}")
                    failures[chat.id] = result.error
                }
            }
        }
        return if (failures.isNotEmpty()) {
            ResultWithError.Failure(PopulateRemoteError(failures))
        } else {
            ResultWithError.Success(Unit)
        }
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

        val failedRemoteOperations = mutableListOf<Pair<String, RemoteDataSourceClearDataError>>()
        // Clear remote server data
        operationsCounter++
        remoteDebugDataSource.clearData().let { result ->
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Failed to clear remote data: ${result.error}")
                failedRemoteOperations.add("clearRemoteData" to result.error)
            }
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

    override suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, UpdateSettingsError> =
        when (val result = localDebugDataSource.updateSettings(transform)) {
            is ResultWithError.Success -> ResultWithError.Success(result.data)
            is ResultWithError.Failure -> ResultWithError.Failure(result.error.toRepositoryError())
        }

    override suspend fun getSettings(): ResultWithError<DebugSettings, GetSettingsError> {
        val result = try {
            settings.first()
        } catch (e: NoSuchElementException) {
            logger.e(TAG, "No data found in DataStore", e)
            return ResultWithError.Failure(GetSettingsError.NoData)
        }
        return when (result) {
            is ResultWithError.Failure -> {
                logger.w(TAG, "Failed to fetch debug settings: ${result.error}")
                ResultWithError.Failure(result.error)
            }

            is ResultWithError.Success -> ResultWithError.Success(result.data)
        }
    }

    private fun startAutoActivity() {
        stopAutoActivity() // Stop any existing activity

        logger.d(TAG, "Starting auto-activity simulation")
        autoActivityJob = coroutineScope.launch {
            while (isActive) {
                // Random delay between 5-15 seconds
                delay(Random.nextLong(AUTO_ACTIVITY_MIN_DELAY_MS, AUTO_ACTIVITY_MAX_DELAY_MS))
                simulateNewMessage()
            }
        }
    }

    private fun stopAutoActivity() {
        autoActivityJob?.cancel()
        autoActivityJob = null
        logger.d(TAG, "Stopped auto-activity simulation")
    }

    /**
     * Get a random existing chat and add a message to it
     */
    private fun simulateNewMessage(): ResultWithError<Unit, SimulateMessageError> {
        val currentChats = remoteDebugDataSource.getChats().let { result ->
            when (result) {
                is ResultWithError.Failure -> {
                    logger.w(TAG, "Failed to fetch chats for auto-activity: ${result.error}")
                    return ResultWithError.Failure(SimulateMessageError.GetChatsError(result.error))
                }

                is ResultWithError.Success -> result.data
            }
        }
        return if (currentChats.isNotEmpty()) {
            val randomChat = currentChats.random()
            val newMessage = sampleDataProvider.generateSingleMessage(randomChat)

            logger.d(TAG, "Simulating new message in chat: ${randomChat.name}")
            val result = remoteDebugDataSource.addMessage(newMessage)
            if (result is ResultWithError.Failure) {
                logger.w(TAG, "Failed to add simulated message: ${result.error}")
                ResultWithError.Failure(SimulateMessageError.AddMessageError(result.error))
            } else {
                ResultWithError.Success(Unit)
            }
        } else {
            logger.d(TAG, "No chats available for auto-activity simulation")
            ResultWithError.Failure(SimulateMessageError.NoChatsAvailable)
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

private fun LocalUpdateSettingsError.toRepositoryError(): UpdateSettingsError = when (this) {
    is LocalUpdateSettingsError.TransformError -> UpdateSettingsError.TransformError(exception)
    is LocalUpdateSettingsError.WriteError -> UpdateSettingsError.WriteError(exception)
}

private sealed interface SimulateMessageError {
    data class GetChatsError(val error: timur.gilfanov.messenger.data.source.remote.GetChatsError) :
        SimulateMessageError

    data class AddMessageError(
        val error: timur.gilfanov.messenger.data.source.remote.AddMessageError,
    ) : SimulateMessageError

    data object NoChatsAvailable : SimulateMessageError
}
