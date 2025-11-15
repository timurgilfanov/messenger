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

class LocalSettingsDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val settingsDao: SettingsDao,
) : LocalSettingsDataSource {

    override fun observe(userId: UserId): Flow<LocalSettings?> =
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
                    null
                } else {
                    LocalSettings.fromEntities(entities)
                }
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

    override suspend fun upsert(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, UpsertSettingError> = try {
        database.withTransaction {
            val entities = settingsDao.getAll(userId = userId.id.toString())
            if (entities.isEmpty()) {
                return@withTransaction Failure(UpsertSettingError.SettingsNotFound)
            }

            val localSettings = LocalSettings.fromEntities(entities)
            val transformedLocalSettings = transform(localSettings)
            val transformedEntities = transformedLocalSettings.toSettingEntities(userId)

            val now = System.currentTimeMillis()
            transformedEntities.forEach { updated ->
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
        private const val MAX_RETRIES = 3L
        private const val INITIAL_BACKOFF_MS = 100L
        private const val MAX_BACKOFF_MS = 2000L
    }
}
