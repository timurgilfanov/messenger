package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteException
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.datastore.SyncPreferences
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

/**
 * Debug-only implementation of LocalDebugDataSource that provides operations
 * for clearing and resetting local data. This is used for debug data generation
 * and testing scenarios. This class is only available in debug builds.
 */
@Singleton
class LocalDebugDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : LocalDebugDataSource {

    private val errorHandler = DatabaseErrorHandler(logger)

    companion object {
        private const val TAG = "LocalDebugDataSource"
    }

    override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> = try {
        logger.d(TAG, "Deleting all chats and chat participants")
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            db.execSQL("DELETE FROM chat_participants")
            db.execSQL("DELETE FROM chats")
        }
        logger.d(TAG, "Successfully deleted all chats")
        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    }

    override suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError> = try {
        logger.d(TAG, "Deleting all messages")
        database.openHelper.writableDatabase.execSQL("DELETE FROM messages")
        logger.d(TAG, "Successfully deleted all messages")
        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    }

    override suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalClearSyncTimestampError> =
        try {
            logger.d(TAG, "Clearing sync timestamp")
            dataStore.edit { preferences ->
                preferences.remove(SyncPreferences.LAST_SYNC_TIMESTAMP)
            }
            logger.d(TAG, "Successfully cleared sync timestamp")
            ResultWithError.Success(Unit)
        } catch (e: IOException) {
            ResultWithError.Failure(LocalClearSyncTimestampError.WriteError(e))
        }

    override val settings: Flow<ResultWithError<DebugSettings, LocalGetSettingsError>> =
        dataStore.data
            .map { preferences ->
                ResultWithError.Success<DebugSettings, LocalGetSettingsError>(
                    DebugSettings.fromPreferences(preferences),
                )
            }
            .retryWhen { cause, attempt ->
                if (cause is IOException && attempt < 3) {
                    logger.w(TAG, "Retrying to read settings (attempt=$attempt)", cause)
                    delay(minOf(2.0.pow(attempt.toInt()), 5.0).seconds)
                    true
                } else {
                    false
                }
            }
            .catch<ResultWithError<DebugSettings, LocalGetSettingsError>> {
                when (it) {
                    is IOException -> {
                        logger.e(TAG, "Error reading debug settings", it)
                        emit(
                            ResultWithError.Failure<DebugSettings, LocalGetSettingsError>(
                                LocalGetSettingsError.ReadError(it),
                            ),
                        )
                    }
                    else -> throw it
                }
            }

    @Suppress("TooGenericExceptionCaught") // We don't control the transform function
    override suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, LocalUpdateSettingsError> = try {
        dataStore.edit { preferences ->
            val currentSettings = DebugSettings.fromPreferences(preferences)
            val updatedSettings = transform(currentSettings)
            updatedSettings.toPreferences(preferences)
        }
        ResultWithError.Success(Unit)
    } catch (e: IOException) {
        ResultWithError.Failure(LocalUpdateSettingsError.WriteError(e))
    } catch (e: Exception) {
        ResultWithError.Failure(LocalUpdateSettingsError.TransformError(e))
    }
}
