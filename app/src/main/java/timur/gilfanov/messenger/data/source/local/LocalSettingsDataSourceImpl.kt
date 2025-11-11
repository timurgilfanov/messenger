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
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDao
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val settingsDao: SettingsDao,
) : LocalSettingsDataSource {

    override fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>> =
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
    override suspend fun update(entity: SettingEntity): ResultWithError<Unit, UpdateSettingError> {
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
                                    UpdateSettingError.ConcurrentModificationError
                                is SQLiteDiskIOException -> UpdateSettingError.DiskIOError
                                else -> error("Unreachable")
                            }
                            return Failure(error)
                        }
                    }

                    is SQLiteFullException ->
                        return Failure(UpdateSettingError.StorageFull)

                    is SQLiteDatabaseCorruptException ->
                        return Failure(UpdateSettingError.DatabaseCorrupted)

                    is SQLiteAccessPermException ->
                        return Failure(UpdateSettingError.AccessDenied)

                    is SQLiteReadOnlyDatabaseException ->
                        return Failure(UpdateSettingError.ReadOnlyDatabase)

                    else ->
                        return Failure(UpdateSettingError.UnknownError(e))
                }
            }
        }
    }

    override suspend fun update(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingError> = try {
        database.withTransaction {
            val entities = settingsDao.getAll(userId = userId.id.toString())
            if (entities.isEmpty()) {
                return@withTransaction Failure(UpdateSettingError.SettingsNotFound)
            }

            val settings = entities.toSettings()
            val transformedSettings = transform(settings)
            val transformedEntities = transformedSettings.toSettingEntities(userId)

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
            is SQLiteFullException -> UpdateSettingError.StorageFull
            is SQLiteDatabaseCorruptException -> UpdateSettingError.DatabaseCorrupted
            is SQLiteAccessPermException -> UpdateSettingError.AccessDenied
            is SQLiteReadOnlyDatabaseException -> UpdateSettingError.ReadOnlyDatabase
            is SQLiteDatabaseLockedException -> UpdateSettingError.ConcurrentModificationError
            is SQLiteDiskIOException -> UpdateSettingError.DiskIOError
            else -> UpdateSettingError.UnknownError(e)
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

private fun List<SettingEntity>.toSettings(): Settings {
    val uiLanguageEntity = find { it.key == SettingKey.UI_LANGUAGE.key }
    val uiLanguage = when (uiLanguageEntity?.value) {
        "German" -> UiLanguage.German
        else -> UiLanguage.English
    }

    val lastModifiedAt = maxOfOrNull { it.modifiedAt } ?: 0L
    val allSynced = all { it.localVersion == it.syncedVersion }
    val lastSyncedAt = if (allSynced && isNotEmpty()) {
        lastModifiedAt
    } else {
        null
    }

    val metadata = SettingsMetadata(
        isDefault = isEmpty(),
        lastModifiedAt = Instant.fromEpochMilliseconds(lastModifiedAt),
        lastSyncedAt = lastSyncedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    return Settings(
        uiLanguage = uiLanguage,
        metadata = metadata,
    )
}

private fun Settings.toSettingEntities(userId: UserId): List<SettingEntity> {
    val uiLanguageValue = when (uiLanguage) {
        UiLanguage.English -> "English"
        UiLanguage.German -> "German"
    }

    return listOf(
        SettingEntity(
            userId = userId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = uiLanguageValue,
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        ),
    )
}
