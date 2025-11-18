package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import androidx.room.withTransaction
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDao
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.util.Logger

/**
 * Room-based implementation of [LocalSettingsDataSource] with automatic retry logic.
 *
 * ## Error Handling Strategy:
 *
 * **Transient Errors (Automatic Retry):**
 * - Database locks ([SQLiteDatabaseLockedException])
 * - Disk I/O errors ([SQLiteDiskIOException])
 * - Retry up to 3 times with exponential backoff (100ms → 200ms → 400ms, max 2s)
 *
 * **Permanent Errors (Immediate Failure):**
 * - Storage full ([SQLiteFullException])
 * - Database corruption ([SQLiteDatabaseCorruptException])
 * - Access denied ([SQLiteAccessPermException])
 * - Read-only database ([SQLiteReadOnlyDatabaseException])
 *
 * ## Method-Specific Behavior:
 * - [observe]: Retries transient errors in Flow, completes Flow on permanent errors
 * - [getSetting], [getUnsyncedSettings]: Retry transient errors, propagate permanent errors
 * - [upsert] (single): Retries transient errors with exponential backoff
 * - [upsert] (batch): **NO RETRY** - fails immediately on any error
 * - [transform]: **NO RETRY** - transaction semantics prevent retry
 *
 * @property database MessengerDatabase instance for transaction support
 * @property settingsDao Room DAO for settings table operations
 * @property logger Diagnostic logger for error tracking
 */
class LocalSettingsDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val settingsDao: SettingsDao,
    private val logger: Logger,
) : LocalSettingsDataSource {

    /**
     * All errors, except NoSettings, will complete the flow and consumer need to resubscribe
     */
    override fun observe(
        userId: UserId,
    ): Flow<ResultWithError<LocalSettings, GetSettingsLocalDataSourceError>> =
        settingsDao.observeAllByUser(userId.id.toString())
            .retryWhen { cause, attempt ->
                when {
                    (cause is SQLiteDatabaseLockedException || cause is SQLiteDiskIOException) &&
                        attempt < MAX_RETRIES -> {
                        delay(calculateBackoff(attempt))
                        true
                    }

                    else -> false
                }
            }
            .map { entities ->
                if (entities.isEmpty()) {
                    Failure<LocalSettings, GetSettingsLocalDataSourceError>(
                        GetSettingsLocalDataSourceError.NoSettings,
                    )
                } else {
                    Success(LocalSettings.fromEntities(entities))
                }
            }.catch { exception ->
                val error = when (exception) {
                    is SQLiteFullException -> {
                        // Can occur during observation due to WAL checkpoints, temp file creation,
                        // or statement compilation
                        logger.e(TAG, "Insufficient storage while observing settings", exception)
                        GetSettingsLocalDataSourceError.Recoverable.InsufficientStorage
                    }

                    is SQLiteDatabaseCorruptException -> {
                        logger.e(TAG, "Database corruption while observing settings", exception)
                        GetSettingsLocalDataSourceError.Recoverable.DataCorruption
                    }

                    is SQLiteAccessPermException -> {
                        logger.e(TAG, "Access denied while observing settings", exception)
                        GetSettingsLocalDataSourceError.Recoverable.AccessDenied
                    }

                    is SQLiteReadOnlyDatabaseException -> {
                        // Room's WAL mode requires write access even for reads (checkpoints).
                        // Can also occur when storage becomes read-only
                        logger.e(TAG, "Read-only database while observing settings", exception)
                        GetSettingsLocalDataSourceError.Recoverable.ReadOnly
                    }

                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        logger.e(
                            TAG,
                            "Transient error while observing settings after retries",
                            exception,
                        )
                        GetSettingsLocalDataSourceError.Recoverable.TemporarilyUnavailable
                    }

                    else -> {
                        logger.e(TAG, "Unknown error while observing settings", exception)
                        GetSettingsLocalDataSourceError.Unknown
                    }
                }
                emit(Failure(error))
            }

    @Suppress("NestedBlockDepth", "ReturnCount")
    override suspend fun getSetting(
        userId: UserId,
        key: String,
    ): ResultWithError<SettingEntity, GetSettingError> {
        var attempt = 0L
        while (true) {
            try {
                val entity = settingsDao.get(userId.id.toString(), key)
                return if (entity != null) {
                    Success(entity)
                } else {
                    Failure(GetSettingError.SettingNotFound)
                }
            } catch (e: SQLiteException) {
                when (e) {
                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        if (attempt < MAX_RETRIES) {
                            delay(calculateBackoff(attempt))
                            attempt++
                        } else {
                            val error = when (e) {
                                is SQLiteDatabaseLockedException ->
                                    GetSettingError.ConcurrentModificationError

                                is SQLiteDiskIOException -> GetSettingError.DiskIOError
                                else -> error("Unreachable")
                            }
                            return Failure(error)
                        }
                    }

                    is SQLiteDatabaseCorruptException ->
                        return Failure(GetSettingError.DatabaseCorrupted)

                    is SQLiteAccessPermException ->
                        return Failure(GetSettingError.AccessDenied)

                    is SQLiteReadOnlyDatabaseException ->
                        return Failure(GetSettingError.ReadOnlyDatabase)

                    else ->
                        return Failure(GetSettingError.UnknownError(e))
                }
            }
        }
    }

    @Suppress("NestedBlockDepth", "ReturnCount")
    override suspend fun upsert(entity: SettingEntity): ResultWithError<Unit, UpsertSettingError> {
        var attempt = 0L
        while (true) {
            try {
                settingsDao.upsert(entity)
                return Success(Unit)
            } catch (e: SQLiteException) {
                when (e) {
                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        if (attempt < MAX_RETRIES) {
                            delay(calculateBackoff(attempt))
                            attempt++
                        } else {
                            val error = when (e) {
                                is SQLiteDatabaseLockedException ->
                                    UpsertSettingError.ConcurrentModificationError

                                is SQLiteDiskIOException -> UpsertSettingError.DiskIOError
                                else -> error("Unreachable")
                            }
                            return Failure(error)
                        }
                    }

                    is SQLiteFullException ->
                        return Failure(UpsertSettingError.StorageFull)

                    is SQLiteDatabaseCorruptException ->
                        return Failure(UpsertSettingError.DatabaseCorrupted)

                    is SQLiteAccessPermException ->
                        return Failure(UpsertSettingError.AccessDenied)

                    is SQLiteReadOnlyDatabaseException ->
                        return Failure(UpsertSettingError.ReadOnlyDatabase)

                    else ->
                        return Failure(UpsertSettingError.UnknownError(e))
                }
            }
        }
    }

    override suspend fun transform(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, TransformSettingError> = try {
        database.withTransaction {
            val entities = settingsDao.getAll(userId = userId.id.toString())
            if (entities.isEmpty()) {
                return@withTransaction Failure(TransformSettingError.SettingsNotFound)
            }

            val localSettings = LocalSettings.fromEntities(entities)
            val transformedLocalSettings = transform(localSettings)
            val transformedEntities = transformedLocalSettings.toSettingEntities(userId)

            val now = System.currentTimeMillis()
            transformedEntities.forEach { updated ->
                // todo can use batch upsert
                val initial = entities.find { it.key == updated.key }
                if (initial != null && updated.value != initial.value) {
                    val modified = updated.copy(
                        localVersion = initial.localVersion + 1,
                        modifiedAt = now,
                        serverVersion = initial.serverVersion,
                        syncedVersion = initial.syncedVersion,
                        syncStatus = SyncStatus.PENDING,
                    )
                    settingsDao.upsert(modified)
                } else if (initial == null) {
                    settingsDao.upsert(updated)
                }
            }

            Success(Unit)
        }
    } catch (e: SQLiteException) {
        val error = when (e) {
            is SQLiteFullException -> TransformSettingError.StorageFull
            is SQLiteDatabaseCorruptException -> TransformSettingError.DatabaseCorrupted
            is SQLiteAccessPermException -> TransformSettingError.AccessDenied
            is SQLiteReadOnlyDatabaseException -> TransformSettingError.ReadOnlyDatabase
            // todo can retry
            is SQLiteDatabaseLockedException -> TransformSettingError.ConcurrentModificationError
            // todo can retry
            is SQLiteDiskIOException -> TransformSettingError.DiskIOError
            else -> {
                logger.e(TAG, "Unknown error on setting upsert in database", e)
                TransformSettingError.UnknownError(e)
            }
        }
        Failure(error)
    }

    override suspend fun upsert(
        entities: List<SettingEntity>,
    ): ResultWithError<Unit, UpsertSettingError> = try {
        settingsDao.upsert(entities)
        Success(Unit)
    } catch (e: SQLiteException) {
        val error = when (e) {
            is SQLiteFullException -> UpsertSettingError.StorageFull
            is SQLiteDatabaseCorruptException -> UpsertSettingError.DatabaseCorrupted
            is SQLiteAccessPermException -> UpsertSettingError.AccessDenied
            is SQLiteReadOnlyDatabaseException -> UpsertSettingError.ReadOnlyDatabase
            is SQLiteDatabaseLockedException -> UpsertSettingError.ConcurrentModificationError
            is SQLiteDiskIOException -> UpsertSettingError.DiskIOError
            else -> UpsertSettingError.UnknownError(e)
        }
        Failure(error)
    }

    @Suppress("NestedBlockDepth", "ReturnCount")
    override suspend fun getUnsyncedSettings(): ResultWithError<
        List<SettingEntity>,
        GetUnsyncedSettingsError,
        > {
        var attempt = 0L
        while (true) {
            try {
                val settings = settingsDao.getUnsynced()
                return Success(settings)
            } catch (e: SQLiteException) {
                when (e) {
                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        if (attempt < MAX_RETRIES) {
                            delay(calculateBackoff(attempt))
                            attempt++
                        } else {
                            val error = when (e) {
                                is SQLiteDatabaseLockedException ->
                                    GetUnsyncedSettingsError.ConcurrentModificationError

                                is SQLiteDiskIOException -> GetUnsyncedSettingsError.DiskIOError
                                else -> error("Unreachable")
                            }
                            return Failure(error)
                        }
                    }

                    is SQLiteDatabaseCorruptException ->
                        return Failure(GetUnsyncedSettingsError.DatabaseCorrupted)

                    is SQLiteAccessPermException ->
                        return Failure(GetUnsyncedSettingsError.AccessDenied)

                    is SQLiteReadOnlyDatabaseException ->
                        return Failure(GetUnsyncedSettingsError.ReadOnlyDatabase)

                    else ->
                        return Failure(GetUnsyncedSettingsError.UnknownError(e))
                }
            }
        }
    }

    private fun calculateBackoff(attempt: Long): Long =
        (INITIAL_BACKOFF_MS * (1 shl attempt.toInt())).coerceAtMost(
            MAX_BACKOFF_MS,
        ).milliseconds.inWholeMilliseconds

    companion object {

        private const val TAG = "LocalSettingsDataSource"
        private const val MAX_RETRIES = 3L
        private const val INITIAL_BACKOFF_MS = 100L
        private const val MAX_BACKOFF_MS = 2000L
    }
}
