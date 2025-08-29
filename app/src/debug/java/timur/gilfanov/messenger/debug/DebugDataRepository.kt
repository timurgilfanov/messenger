package timur.gilfanov.messenger.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random
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
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.debug.datastore.DebugPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.util.Logger

@Suppress("TooManyFunctions", "LongParameterList", "TooGenericExceptionCaught") // Debug code
@Singleton
class DebugDataRepository @Inject constructor(
    @param:Named("debug") private val dataStore: DataStore<Preferences>,
    private val localDebugDataSource: LocalDebugDataSource,
    private val localDataSources: LocalDataSources,
    private val remoteDebugDataSource: RemoteDebugDataSource,
    private val sampleDataProvider: SampleDataProvider,
    @param:Named("debug") private val coroutineScope: CoroutineScope,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "DebugDataRepository"
        private const val AUTO_ACTIVITY_MIN_DELAY_MS = 5000L
        private const val AUTO_ACTIVITY_MAX_DELAY_MS = 15000L
    }

    private var autoActivityJob: Job? = null

    /**
     * Flow of current debug settings
     */
    val debugSettings: Flow<DebugSettings> = dataStore.data.map { preferences ->
        DebugSettings.fromPreferences(preferences)
    }

    init {
        // Monitor auto-activity setting and start/stop simulation accordingly
        debugSettings
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
    suspend fun initializeWithScenario(scenario: DataScenario) {
        logger.d(TAG, "Initializing with scenario: ${scenario.name}")

        // Update settings with the new scenario
        updateSettings { current ->
            current.copy(scenario = scenario)
        }

        // Generate and populate data
        regenerateData(scenario)
    }

    /**
     * Regenerate data using current scenario
     */
    suspend fun regenerateData() {
        val currentSettings = debugSettings.first()
        regenerateData(currentSettings.scenario)
    }

    /**
     * Regenerate data using specified scenario
     */
    private suspend fun regenerateData(scenario: DataScenario) {
        logger.d(TAG, "Regenerating data for scenario: ${scenario.name}")

        // Get current sync timestamp to ensure generated data is newer
        val lastSyncTimestamp = when (val result = localDataSources.sync.getLastSyncTimestamp()) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> {
                logger.w(TAG, "Failed to get last sync timestamp: ${result.error}, using epoch")
                null
            }
        }
        logger.d(TAG, "Current sync timestamp: $lastSyncTimestamp")

        // Clear existing data from both local and remote sources
        logger.d(TAG, "Clearing local database cache")
        try {
            localDebugDataSource.deleteAllChats()
            localDebugDataSource.deleteAllMessages()
            localDebugDataSource.clearSyncTimestamp()
            logger.d(TAG, "Local cache cleared successfully")
        } catch (e: Exception) {
            logger.w(TAG, "Failed to clear local cache", e)
            // Continue anyway - the sync will overwrite with new data
        }

        // Initialize remote server with timestamp newer than last sync before clearing
        // This ensures generated data appears as "new" to sync operations
        lastSyncTimestamp?.let { timestamp ->
            logger.d(TAG, "Setting initial timestamp to $timestamp for hybrid approach")
            remoteDebugDataSource.setInitialTimestamp(timestamp)
        }

        logger.d(TAG, "Clearing remote server data")
        remoteDebugDataSource.clearServerData()

        // Generate new data
        val config = scenario.toConfig()
        val chats = sampleDataProvider.generateChats(config)

        logger.d(TAG, "Generated ${chats.size} chats with scenario ${scenario.name}")

        // Populate server with new data
        chats.forEach { chat ->
            // Fix the recipient field in messages to match the chat ID
            val fixedChat = fixMessageRecipients(chat)
            remoteDebugDataSource.addChatToServer(fixedChat)
        }

        // Update generation timestamp
        dataStore.edit { preferences ->
            preferences[DebugPreferences.LAST_DATA_GENERATION] = System.currentTimeMillis()
            preferences[DebugPreferences.LAST_USED_SCENARIO] = scenario.name
        }

        logger.d(TAG, "Data generation complete - sync will refresh UI with new data")
    }

    /**
     * Clear all data
     */
    suspend fun clearAllData() {
        logger.d(TAG, "Clearing all debug data")

        // Clear local database cache
        logger.d(TAG, "Clearing local database cache")
        try {
            localDebugDataSource.deleteAllChats()
            localDebugDataSource.deleteAllMessages()
            localDebugDataSource.clearSyncTimestamp()
            logger.d(TAG, "Local cache cleared successfully")
        } catch (e: Exception) {
            logger.w(TAG, "Failed to clear local cache", e)
        }

        // Clear remote server data
        remoteDebugDataSource.clearServerData()

        dataStore.edit { preferences ->
            preferences[DebugPreferences.LAST_DATA_GENERATION] = System.currentTimeMillis()
        }
    }

    /**
     * Update debug settings
     */
    suspend fun updateSettings(transform: (DebugSettings) -> DebugSettings) {
        dataStore.edit { preferences ->
            val currentSettings = DebugSettings.fromPreferences(preferences)
            val updatedSettings = transform(currentSettings)
            updatedSettings.toPreferences(preferences)
        }
    }

    /**
     * Get saved scenario from preferences, if any
     */
    suspend fun getSavedScenario(): DataScenario? =
        dataStore.data.first()[DebugPreferences.DATA_SCENARIO]?.let { scenarioName ->
            try {
                DataScenario.valueOf(scenarioName)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Invalid saved scenario: $scenarioName", e)
                null
            }
        }

    /**
     * Toggle auto-activity feature
     */
    suspend fun toggleAutoActivity(enabled: Boolean) {
        updateSettings { current ->
            current.copy(autoActivity = enabled)
        }
    }

    /**
     * Toggle debug notification visibility
     */
    suspend fun toggleNotification(show: Boolean) {
        updateSettings { current ->
            current.copy(showNotification = show)
        }
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
            val currentChats = remoteDebugDataSource.getCurrentChats()
            if (currentChats.isNotEmpty()) {
                val randomChat = currentChats.random()
                val newMessage = sampleDataProvider.generateSingleMessage(randomChat)

                logger.d(TAG, "Simulating new message in chat: ${randomChat.name}")
                remoteDebugDataSource.addMessageToServerChat(newMessage)
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
                is timur.gilfanov.messenger.domain.entity.message.TextMessage -> {
                    message.copy(recipient = chat.id)
                }
                // Add other message types here if needed
                else -> message
            }
        }

        return chat.copy(messages = fixedMessages.toPersistentList())
    }
}
