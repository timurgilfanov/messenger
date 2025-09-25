package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Debug-only data source interface for operations that clear or reset local data.
 * These operations are used for debug data generation and testing scenarios.
 * This interface is only available in debug builds.
 */
interface LocalDebugDataSource {

    /**
     * Deletes all chats from local storage.
     * Used when regenerating debug data to start with a clean slate.
     */
    suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Deletes all messages from local storage.
     * Used when regenerating debug data to start with a clean slate.
     */
    suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Clears the sync timestamp to force a full resync from the beginning.
     * Used when regenerating debug data to ensure sync starts fresh.
     */
    suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalClearSyncTimestampError>

    val settings: Flow<DebugSettings>

    suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, LocalUpdateSettingsError>
}
