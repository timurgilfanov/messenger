package timur.gilfanov.messenger.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.datastore.SyncPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

/**
 * Debug-only implementation of LocalDebugDataSource that provides operations
 * for clearing and resetting local data. This is used for debug data generation
 * and testing scenarios. This class is only available in debug builds.
 */
@Suppress("TooGenericExceptionCaught") // Debug code
@Singleton
class LocalDebugDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : LocalDebugDataSource {

    companion object {
        private const val TAG = "LocalDebugDataSource"
    }

    override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> = try {
        logger.d(TAG, "Deleting all chats and chat participants")
        // Use raw SQL queries through database
        database.openHelper.writableDatabase.execSQL("DELETE FROM chat_participants")
        database.openHelper.writableDatabase.execSQL("DELETE FROM chats")
        logger.d(TAG, "Successfully deleted all chats")
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        logger.w(TAG, "Failed to delete all chats", e)
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError> = try {
        logger.d(TAG, "Deleting all messages")
        database.openHelper.writableDatabase.execSQL("DELETE FROM messages")
        logger.d(TAG, "Successfully deleted all messages")
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        logger.w(TAG, "Failed to delete all messages", e)
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalDataSourceError> = try {
        logger.d(TAG, "Clearing sync timestamp")
        dataStore.edit { preferences ->
            preferences.remove(SyncPreferences.LAST_SYNC_TIMESTAMP)
        }
        logger.d(TAG, "Successfully cleared sync timestamp")
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        logger.w(TAG, "Failed to clear sync timestamp", e)
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }
}
