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
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
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
    private val defaultSettings: Settings,
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
                    Success(LocalSettings.fromEntities(entities, defaultSettings))
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

    @Suppress("NestedBlockDepth", "ReturnCount", "LongMethod")
    override suspend fun getSetting(
        userId: UserId,
        key: SettingKey,
    ): ResultWithError<TypedLocalSetting, GetSettingError> {
        var attempt = 0L
        while (true) {
            try {
                val entity = settingsDao.get(userId.id.toString(), key.key)
                return if (entity != null) {
                    Success(entity.toTypedLocalSetting(defaultSettings))
                } else {
                    logger.w(
                        TAG,
                        "Setting ${key.key} not found for user ${userId.id}",
                    )
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
                            logger.e(
                                TAG,
                                "Failed to get setting ${key.key} for user ${userId.id} after " +
                                    "$MAX_RETRIES retries due to $error",
                                e,
                            )
                            return Failure(error)
                        }
                    }

                    is SQLiteDatabaseCorruptException -> {
                        logger.e(
                            TAG,
                            "Database corrupted while getting setting ${key.key} for user " +
                                userId.id,
                            e,
                        )
                        return Failure(GetSettingError.DatabaseCorrupted)
                    }

                    is SQLiteAccessPermException -> {
                        logger.e(
                            TAG,
                            "Access denied while getting setting ${key.key} for user ${userId.id}",
                            e,
                        )
                        return Failure(GetSettingError.AccessDenied)
                    }

                    is SQLiteReadOnlyDatabaseException -> {
                        logger.e(
                            TAG,
                            "Read-only database while getting setting ${key.key} for user " +
                                userId.id,
                            e,
                        )
                        return Failure(GetSettingError.ReadOnlyDatabase)
                    }

                    else -> {
                        logger.e(
                            TAG,
                            "Unknown database error while getting setting ${key.key} for user " +
                                userId.id,
                            e,
                        )
                        return Failure(GetSettingError.UnknownError(e))
                    }
                }
            }
        }
    }

    @Suppress("NestedBlockDepth", "ReturnCount", "LongMethod")
    override suspend fun upsert(
        userId: UserId,
        setting: TypedLocalSetting,
    ): ResultWithError<Unit, UpsertSettingError> {
        var attempt = 0L
        while (true) {
            try {
                val entity = setting.toSettingEntity(userId)
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
                            logger.e(
                                TAG,
                                "Failed to upsert setting ${setting.key.key} for user " +
                                    "${userId.id} after $MAX_RETRIES retries due to $error",
                                e,
                            )
                            return Failure(error)
                        }
                    }

                    is SQLiteFullException -> {
                        logger.e(
                            TAG,
                            "Storage full while upserting setting ${setting.key.key} for user " +
                                userId.id,
                            e,
                        )
                        return Failure(UpsertSettingError.StorageFull)
                    }

                    is SQLiteDatabaseCorruptException -> {
                        logger.e(
                            TAG,
                            "Database corrupted while upserting setting ${setting.key.key} " +
                                "for user ${userId.id}",
                            e,
                        )
                        return Failure(UpsertSettingError.DatabaseCorrupted)
                    }

                    is SQLiteAccessPermException -> {
                        logger.e(
                            TAG,
                            "Access denied while upserting setting ${setting.key.key} for user " +
                                userId.id,
                            e,
                        )
                        return Failure(UpsertSettingError.AccessDenied)
                    }

                    is SQLiteReadOnlyDatabaseException -> {
                        logger.e(
                            TAG,
                            "Read-only database while upserting setting ${setting.key.key} " +
                                "for user ${userId.id}",
                            e,
                        )
                        return Failure(UpsertSettingError.ReadOnlyDatabase)
                    }

                    else -> {
                        logger.e(
                            TAG,
                            "Unknown database error while upserting setting ${setting.key.key} " +
                                "for user ${userId.id}",
                            e,
                        )
                        return Failure(UpsertSettingError.UnknownError(e))
                    }
                }
            }
        }
    }

    @Suppress("LongMethod")
    override suspend fun transform(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, TransformSettingError> = try {
        database.withTransaction {
            val entities = getSettingsWithRetry(userId)
            if (entities.isEmpty()) {
                return@withTransaction Failure(TransformSettingError.SettingsNotFound)
            }

            val localSettings = LocalSettings.fromEntities(entities, defaultSettings)
            val transformedLocalSettings = transform(localSettings)
            val transformedEntities = transformedLocalSettings.toSettingEntities(userId)

            val now = System.currentTimeMillis()
            val entitiesToUpsert = buildList {
                transformedEntities.forEach { updated ->
                    val initial = entities.find { it.key == updated.key }
                    if (initial != null && updated.value != initial.value) {
                        add(
                            updated.copy(
                                localVersion = initial.localVersion + 1,
                                modifiedAt = now,
                                serverVersion = initial.serverVersion,
                                syncedVersion = initial.syncedVersion,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        )
                    } else if (initial == null) {
                        add(updated)
                    }
                }
            }

            settingsDao.upsert(entitiesToUpsert)

            Success(Unit)
        }
    } catch (e: SQLiteException) {
        val error = when (e) {
            is SQLiteFullException -> {
                logger.e(TAG, "Storage full while transforming settings for user ${userId.id}", e)
                TransformSettingError.StorageFull
            }
            is SQLiteDatabaseCorruptException -> {
                logger.e(
                    TAG,
                    "Database corrupted while transforming settings for user ${userId.id}",
                    e,
                )
                TransformSettingError.DatabaseCorrupted
            }
            is SQLiteAccessPermException -> {
                logger.e(TAG, "Access denied while transforming settings for user ${userId.id}", e)
                TransformSettingError.AccessDenied
            }
            is SQLiteReadOnlyDatabaseException -> {
                logger.e(
                    TAG,
                    "Read-only database while transforming settings for user ${userId.id}",
                    e,
                )
                TransformSettingError.ReadOnlyDatabase
            }
            is SQLiteDatabaseLockedException -> {
                logger.e(
                    TAG,
                    "Concurrent modification error while transforming settings for user ${userId.id}",
                    e,
                )
                TransformSettingError.ConcurrentModificationError
            }
            is SQLiteDiskIOException -> {
                logger.e(TAG, "Disk IO error while transforming settings for user ${userId.id}", e)
                TransformSettingError.DiskIOError
            }
            else -> {
                logger.e(
                    TAG,
                    "Unknown error on setting transform in database for user ${userId.id}",
                    e,
                )
                TransformSettingError.UnknownError(e)
            }
        }
        Failure(error)
    }

    @Suppress("LongMethod")
    override suspend fun upsert(
        userId: UserId,
        settings: List<TypedLocalSetting>,
    ): ResultWithError<Unit, UpsertSettingError> = try {
        val entities = settings.map { it.toSettingEntity(userId) }
        settingsDao.upsert(entities)
        Success(Unit)
    } catch (e: SQLiteException) {
        val keys = settings.joinToString { it.key.key }
        val error = when (e) {
            is SQLiteFullException -> {
                logger.e(
                    TAG,
                    "Storage full while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.StorageFull
            }
            is SQLiteDatabaseCorruptException -> {
                logger.e(
                    TAG,
                    "Database corrupted while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.DatabaseCorrupted
            }
            is SQLiteAccessPermException -> {
                logger.e(
                    TAG,
                    "Access denied while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.AccessDenied
            }
            is SQLiteReadOnlyDatabaseException -> {
                logger.e(
                    TAG,
                    "Read-only database while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.ReadOnlyDatabase
            }
            is SQLiteDatabaseLockedException -> {
                logger.e(
                    TAG,
                    "Concurrent modification error while bulk upserting settings [$keys] " +
                        "for ${userId.id}",
                    e,
                )
                UpsertSettingError.ConcurrentModificationError
            }
            is SQLiteDiskIOException -> {
                logger.e(
                    TAG,
                    "Disk IO error while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.DiskIOError
            }
            else -> {
                logger.e(
                    TAG,
                    "Unknown database error while bulk upserting settings [$keys] for ${userId.id}",
                    e,
                )
                UpsertSettingError.UnknownError(e)
            }
        }
        Failure(error)
    }

    @Suppress("NestedBlockDepth", "ReturnCount", "LongMethod")
    override suspend fun getUnsyncedSettings(
        userId: UserId,
    ): ResultWithError<
        List<TypedLocalSetting>,
        GetUnsyncedSettingsError,
        > {
        var attempt = 0L
        while (true) {
            try {
                val entities = settingsDao.getUnsynced(userId.id.toString())
                val typedSettings = entities.map { it.toTypedLocalSetting(defaultSettings) }
                return Success(typedSettings)
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
                            logger.e(
                                TAG,
                                "Failed to fetch unsynced settings for ${userId.id} after " +
                                    "$MAX_RETRIES retries due to $error",
                                e,
                            )
                            return Failure(error)
                        }
                    }

                    is SQLiteDatabaseCorruptException -> {
                        logger.e(
                            TAG,
                            "Database corrupted while fetching unsynced settings for ${userId.id}",
                            e,
                        )
                        return Failure(GetUnsyncedSettingsError.DatabaseCorrupted)
                    }

                    is SQLiteAccessPermException -> {
                        logger.e(
                            TAG,
                            "Access denied while fetching unsynced settings for ${userId.id}",
                            e,
                        )
                        return Failure(GetUnsyncedSettingsError.AccessDenied)
                    }

                    is SQLiteReadOnlyDatabaseException -> {
                        logger.e(
                            TAG,
                            "Read-only database while fetching unsynced settings for ${userId.id}",
                            e,
                        )
                        return Failure(GetUnsyncedSettingsError.ReadOnlyDatabase)
                    }

                    else -> {
                        logger.e(
                            TAG,
                            "Unknown error while fetching unsynced settings for ${userId.id}",
                            e,
                        )
                        return Failure(GetUnsyncedSettingsError.UnknownError(e))
                    }
                }
            }
        }
    }

    @Suppress("NestedBlockDepth", "ReturnCount")
    private suspend fun getSettingsWithRetry(userId: UserId): List<SettingEntity> {
        var attempt = 0L
        while (true) {
            try {
                return settingsDao.getAll(userId.id.toString())
            } catch (e: SQLiteException) {
                when (e) {
                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        if (attempt < MAX_RETRIES) {
                            delay(calculateBackoff(attempt))
                            attempt++
                        } else {
                            logger.e(
                                TAG,
                                "Failed to load settings for user ${userId.id} after " +
                                    "$MAX_RETRIES retries due to database lock/disk error",
                                e,
                            )
                            throw e
                        }
                    }

                    else -> {
                        logger.e(
                            TAG,
                            "Unexpected error while loading settings for user ${userId.id}",
                            e,
                        )
                        throw e
                    }
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

/**
 * Converts [SettingEntity] to [TypedLocalSetting] with validation.
 *
 * Maps database string value to typed domain value, applying validation during conversion.
 * Falls back to default value if validation fails.
 *
 * @param defaults Default settings to use for validation fallback
 * @return Typed local setting with validated domain value
 */
private fun SettingEntity.toTypedLocalSetting(defaults: Settings): TypedLocalSetting =
    when (SettingKey.fromKey(this.key)) {
        SettingKey.UI_LANGUAGE -> {
            TypedLocalSetting.UiLanguage(
                setting = LocalSetting(
                    value = this.value.toUiLanguageOrDefault(defaults.uiLanguage),
                    localVersion = this.localVersion,
                    syncedVersion = this.syncedVersion,
                    serverVersion = this.serverVersion,
                    modifiedAt = this.modifiedAt,
                    syncStatus = this.syncStatus,
                ),
            )
        }
        SettingKey.THEME -> throw NotImplementedError("${this.key} is not supported")
        SettingKey.NOTIFICATIONS -> throw NotImplementedError("${this.key} is not supported")
        null -> error("Unknown setting key: ${this.key}")
    }

/**
 * Converts [TypedLocalSetting] to [SettingEntity] for database persistence.
 *
 * Maps typed domain value to database string representation.
 *
 * @param userId The user ID to associate with this setting entity
 * @return Setting entity ready for Room database upsert
 */
private fun TypedLocalSetting.toSettingEntity(userId: UserId): SettingEntity = when (this) {
    is TypedLocalSetting.UiLanguage -> SettingEntity(
        userId = userId.id.toString(),
        key = this.key.key,
        value = this.setting.value.toStorageValue(),
        localVersion = this.setting.localVersion,
        syncedVersion = this.setting.syncedVersion,
        serverVersion = this.setting.serverVersion,
        modifiedAt = this.setting.modifiedAt,
        syncStatus = this.setting.syncStatus,
    )
}
