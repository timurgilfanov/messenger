@file:Suppress("ForbiddenComment")

package timur.gilfanov.messenger.data.repository

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.LocalSetting
import timur.gilfanov.messenger.data.source.local.LocalSettings
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.UpdateSettingError
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.local.toStorageValue
import timur.gilfanov.messenger.data.source.local.toUiLanguageOrNull
import timur.gilfanov.messenger.data.source.remote.RemoteSetting
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.SettingSyncRequest
import timur.gilfanov.messenger.data.source.remote.SyncResult
import timur.gilfanov.messenger.data.worker.SyncOutcome
import timur.gilfanov.messenger.data.worker.SyncSettingWorker
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ApplyRemoteSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError
import timur.gilfanov.messenger.util.Logger

// TODO Update KDoc
/**
 * Implementation of [SettingsRepository] that manages user settings with local caching,
 * remote backup, and conflict resolution.
 *
 * ## Current Architecture (On-Demand Recovery):
 * - Settings are fetched on-demand when [observeSettings] is called or operations fail
 * - Recovery triggered when settings are in EMPTY state
 * - Conflicts detected during recovery and propagated to use case layer
 *
 * ## Planned Architecture (Unified Sync Channel):
 * Settings will be synchronized in real-time through a unified sync channel shared with
 * [MessengerRepositoryImpl]. This provides proactive updates instead of reactive recovery.
 *
 * ### Implementation Plan:
 *
 * 1. **Subscribe to Unified Sync Stream** (in init block):
 * ```kotlin
 * init {
 *     syncDataSource.deltaUpdates(identity, lastSync)
 *         .mapNotNull { result -> result.getOrNull()?.settingsChange }
 *         .onEach { remoteSettings -> applySyncUpdate(remoteSettings) }
 *         .launchIn(repositoryScope)
 * }
 * ```
 *
 * 2. **Add applySyncUpdate() Method**:
 * ```kotlin
 * private suspend fun applySyncUpdate(remoteSettings: Settings) {
 *     val currentLocal = localDataSource.observeSettings(userId).first().getOrNull()
 *         ?: return  // No local settings, apply remote directly
 *
 *     when {
 *         // Remote is stale, ignore
 *         remoteSettings.metadata.lastModifiedAt <= currentLocal.metadata.lastSyncedAt -> {
 *             logger.d(TAG, "Ignoring stale sync update")
 *         }
 *
 *         // Local has unsaved changes + remote is newer = conflict
 *         currentLocal.metadata.state == SettingsState.MODIFIED &&
 *         remoteSettings.metadata.lastModifiedAt > currentLocal.metadata.lastSyncedAt -> {
 *             // Emit conflict event for UI to resolve
 *             _settingsConflicts.emit(SettingsConflict(currentLocal, remoteSettings))
 *         }
 *
 *         // Remote is newer and no local changes, apply directly
 *         else -> {
 *             localDataSource.insertSettings(userId, remoteSettings)
 *         }
 *     }
 * }
 * ```
 *
 * 3. **Add Conflict Events Flow**:
 * ```kotlin
 * private val _settingsConflicts = MutableSharedFlow<SettingsConflict>(
 *     replay = 0,
 *     extraBufferCapacity = 1
 * )
 * val settingsConflicts: SharedFlow<SettingsConflict> = _settingsConflicts.asSharedFlow()
 * ```
 *
 * 4. **Keep Current Recovery Flow**:
 * - Maintain [performRecovery] for reliability when sync is unavailable
 * - Recovery acts as fallback when sync channel hasn't started yet
 * - Ensures settings available even without active sync
 *
 * ### Conflict Resolution Strategy:
 * - **Last-write-wins with user intervention**: If both local and remote modified since last sync,
 *   emit conflict event and let UI show dialog for user to choose
 * - **Local wins temporarily**: User sees their change immediately, conflict resolved async
 * - **Timestamp-based ordering**: Use lastSyncedAt to detect truly conflicting changes
 *
 * ### Benefits of Unified Sync:
 * - Real-time updates from other devices
 * - Single network connection shared with messenger sync
 * - Consistent timestamps across all entity types
 * - Reduced recovery overhead
 * - Better UX with proactive sync
 *
 * @see timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource for unified sync channel details
 * @see MessengerRepositoryImpl for chat sync implementation
 */
// TODO: Perform recovery for empty settings from local data source
@Suppress("TooManyFunctions")
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
    private val workManager: WorkManager,
    private val logger: Logger,
) : SettingsRepository {

    // TODO: Why we need extra buffer capacity?
    private val conflictEvents = MutableSharedFlow<SettingsConflictEvent>(
        extraBufferCapacity = CONFLICT_EVENT_BUFFER_CAPACITY,
    )

    companion object {
        private const val TAG = "SettingsRepository"
        private const val CONFLICT_EVENT_BUFFER_CAPACITY = 10
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val BACKOFF_DELAY_SECONDS = 15L
    }

    override fun observeConflicts(): Flow<SettingsConflictEvent> = conflictEvents.asSharedFlow()

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        localDataSource.observe(identity.userId)
            .map { localSettings ->
                if (localSettings == null) {
                    recoverSettings(identity)
                } else {
                    ResultWithError.Success(localSettings.toDomain())
                }
            }
            .catch { exception ->
                val error = when (exception) {
                    is SQLiteFullException -> {
                        logger.e(TAG, "Insufficient storage while observing settings", exception)
                        GetSettingsRepositoryError.Recoverable.InsufficientStorage
                    }

                    is SQLiteDatabaseCorruptException -> {
                        logger.e(TAG, "Database corruption while observing settings", exception)
                        GetSettingsRepositoryError.Recoverable.DataCorruption
                    }

                    is SQLiteAccessPermException -> {
                        logger.e(TAG, "Access denied while observing settings", exception)
                        GetSettingsRepositoryError.Recoverable.AccessDenied
                    }

                    is SQLiteReadOnlyDatabaseException -> {
                        logger.e(TAG, "Read-only database while observing settings", exception)
                        GetSettingsRepositoryError.Recoverable.ReadOnly
                    }

                    is SQLiteDatabaseLockedException,
                    is SQLiteDiskIOException,
                    -> {
                        logger.e(
                            TAG,
                            "Transient error while observing settings after retries",
                            exception,
                        )
                        GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable
                    }

                    else -> {
                        logger.e(TAG, "Unknown error while observing settings", exception)
                        GetSettingsRepositoryError.Unknown
                    }
                }
                emit(ResultWithError.Failure(error))
            }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> {
        val userId = identity.userId
        val key = SettingKey.UI_LANGUAGE.key

        return localDataSource.update(userId) { localSettings ->
            localSettings.copy(
                uiLanguage = localSettings.uiLanguage.copy(value = language),
            )
        }.fold(
            onSuccess = {
                scheduleWorkManagerSync(userId, key)
                ResultWithError.Success(Unit)
            },

            onFailure = { error ->
                when (error) {
                    UpdateSettingError.SettingsNotFound -> {
                        recoverSettings(identity).foldWithErrorMapping(
                            onSuccess = {
                                changeUiLanguage(identity, language)
                            },
                            onFailure = { error ->
                                when (error) {
                                    GetSettingsRepositoryError.Recoverable.AccessDenied -> TODO()
                                    GetSettingsRepositoryError.Recoverable.DataCorruption -> TODO()
                                    GetSettingsRepositoryError.Recoverable.InsufficientStorage ->
                                        ChangeLanguageRepositoryError.InsufficientStorage

                                    GetSettingsRepositoryError.Recoverable.ReadOnly,
                                    -> TODO()

                                    GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable ->
                                        TODO()

                                    GetSettingsRepositoryError.SettingsEmpty -> TODO()
                                    GetSettingsRepositoryError.SettingsResetToDefaults -> TODO()
                                    GetSettingsRepositoryError.Unknown -> TODO()
                                }
                            },
                        )
                    }

                    UpdateSettingError.ConcurrentModificationError -> TODO()
                    UpdateSettingError.DiskIOError -> TODO()
                    UpdateSettingError.DatabaseCorrupted -> TODO()
                    UpdateSettingError.AccessDenied -> TODO()
                    UpdateSettingError.ReadOnlyDatabase -> TODO()
                    is UpdateSettingError.UnknownError -> TODO()

                    UpdateSettingError.StorageFull ->
                        ResultWithError.Failure(ChangeLanguageRepositoryError.InsufficientStorage)
                }
            },
        )
    }

    // TODO Do we need this?
    override suspend fun applyRemoteSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, ApplyRemoteSettingsRepositoryError> = ResultWithError.Success(Unit)

    // TODO Do we need this?
    override suspend fun syncLocalToRemote(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, SyncLocalToRemoteRepositoryError> = ResultWithError.Success(Unit)

    private fun scheduleWorkManagerSync(userId: UserId, key: String) {
        val userIdString = userId.id.toString()
        val workRequest = OneTimeWorkRequestBuilder<SyncSettingWorker>()
            .setInputData(
                workDataOf(
                    SyncSettingWorker.KEY_USER_ID to userIdString,
                    SyncSettingWorker.KEY_SETTING_KEY to key,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .setInitialDelay(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "sync_setting_${userIdString}_$key",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    @Suppress("LongMethod", "ReturnCount", "ComplexMethod", "NestedBlockDepth")
    suspend fun syncSetting(userId: UserId, key: SettingKey): SyncOutcome {
        val entity = when (val result = localDataSource.getSetting(userId, key.key)) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> return SyncOutcome.Failure
        }

        if (entity.localVersion == entity.syncedVersion) {
            return SyncOutcome.Success
        }

        val request = SettingSyncRequest(
            userId = userId,
            key = key.key,
            value = entity.value,
            clientVersion = entity.localVersion,
            lastKnownServerVersion = entity.serverVersion,
            modifiedAt = entity.modifiedAt,
        )

        return when (val result = remoteDataSource.syncSingleSetting(request)) {
            is SyncResult.Success -> {
                when (
                    localDataSource.update(
                        entity.copy(
                            syncedVersion = entity.localVersion,
                            serverVersion = result.newVersion,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                ) {
                    is ResultWithError.Success -> SyncOutcome.Success
                    is ResultWithError.Failure -> SyncOutcome.Retry
                }
            }

            is SyncResult.Conflict -> {
                if (entity.modifiedAt >= result.serverModifiedAt) {
                    when (
                        localDataSource.update(
                            entity.copy(
                                syncedVersion = entity.localVersion,
                                serverVersion = result.newVersion,
                                syncStatus = SyncStatus.SYNCED,
                            ),
                        )
                    ) {
                        is ResultWithError.Success -> SyncOutcome.Success
                        is ResultWithError.Failure -> SyncOutcome.Retry
                    }
                } else {
                    val validatedValue = mapServerValueToLocalValue(
                        settingKey = key,
                        serverValue = result.serverValue,
                        fallbackValue = entity.value,
                    )

                    when (
                        localDataSource.update(
                            entity.copy(
                                value = validatedValue,
                                localVersion = result.newVersion,
                                syncedVersion = result.newVersion,
                                serverVersion = result.newVersion,
                                modifiedAt = result.serverModifiedAt,
                                syncStatus = SyncStatus.SYNCED,
                            ),
                        )
                    ) {
                        is ResultWithError.Success -> {
                            conflictEvents.emit(
                                SettingsConflictEvent(
                                    settingKey = key,
                                    yourValue = entity.value,
                                    acceptedValue = validatedValue,
                                    conflictedAt = Instant.fromEpochMilliseconds(
                                        result.serverModifiedAt,
                                    ),
                                ),
                            )
                            SyncOutcome.Success
                        }

                        is ResultWithError.Failure -> SyncOutcome.Retry
                    }
                }
            }

            is SyncResult.Error -> {
                when (
                    localDataSource.update(
                        entity.copy(syncStatus = SyncStatus.FAILED),
                    )
                ) {
                    is ResultWithError.Success -> SyncOutcome.Retry
                    is ResultWithError.Failure -> SyncOutcome.Retry
                }
            }
        }
    }

    @Suppress(
        "LongMethod",
        "NestedBlockDepth",
        "TooGenericExceptionCaught",
        "SwallowedException",
        "ReturnCount",
        "CyclomaticComplexMethod",
    )
    suspend fun syncAllPendingSettings(): SyncOutcome {
        val unsyncedSettings = when (val result = localDataSource.getUnsyncedSettings()) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> return SyncOutcome.Retry
        }

        if (unsyncedSettings.isEmpty()) {
            return SyncOutcome.Success
        }

        val requests = unsyncedSettings.map { entity ->
            SettingSyncRequest(
                userId = UserId(UUID.fromString((entity.userId))),
                key = entity.key,
                value = entity.value,
                clientVersion = entity.localVersion,
                lastKnownServerVersion = entity.serverVersion,
                modifiedAt = entity.modifiedAt,
            )
        }

        return try {
            val results = remoteDataSource.syncBatch(requests)

            var hasFailures = false
            results.forEach { (key, result) ->
                val entity = unsyncedSettings.find { it.key == key } ?: return@forEach

                when (result) {
                    is SyncResult.Success -> {
                        when (
                            localDataSource.update(
                                entity.copy(
                                    syncedVersion = entity.localVersion,
                                    serverVersion = result.newVersion,
                                    syncStatus = SyncStatus.SYNCED,
                                ),
                            )
                        ) {
                            is ResultWithError.Success -> Unit
                            is ResultWithError.Failure -> hasFailures = true
                        }
                    }

                    is SyncResult.Conflict -> {
                        if (entity.modifiedAt >= result.serverModifiedAt) {
                            when (
                                localDataSource.update(
                                    entity.copy(
                                        syncedVersion = entity.localVersion,
                                        serverVersion = result.newVersion,
                                        syncStatus = SyncStatus.SYNCED,
                                    ),
                                )
                            ) {
                                is ResultWithError.Success -> Unit
                                is ResultWithError.Failure -> hasFailures = true
                            }
                        } else {
                            val settingKey = SettingKey.fromKey(key)
                            if (settingKey == null) {
                                logger.w(TAG, "Unknown setting key during batch sync: $key")
                                return@forEach
                            }

                            val validatedValue = mapServerValueToLocalValue(
                                settingKey = settingKey,
                                serverValue = result.serverValue,
                                fallbackValue = entity.value,
                            )

                            when (
                                localDataSource.update(
                                    entity.copy(
                                        value = validatedValue,
                                        localVersion = result.newVersion,
                                        syncedVersion = result.newVersion,
                                        serverVersion = result.newVersion,
                                        modifiedAt = result.serverModifiedAt,
                                        syncStatus = SyncStatus.SYNCED,
                                    ),
                                )
                            ) {
                                is ResultWithError.Success -> {
                                    conflictEvents.emit(
                                        SettingsConflictEvent(
                                            settingKey = settingKey,
                                            yourValue = entity.value,
                                            acceptedValue = validatedValue,
                                            conflictedAt = Instant.fromEpochMilliseconds(
                                                result.serverModifiedAt,
                                            ),
                                        ),
                                    )
                                }

                                is ResultWithError.Failure -> hasFailures = true
                            }
                        }
                    }

                    is SyncResult.Error -> {
                        when (
                            localDataSource.update(
                                entity.copy(syncStatus = SyncStatus.FAILED),
                            )
                        ) {
                            is ResultWithError.Success -> Unit
                            is ResultWithError.Failure -> Unit
                        }
                        hasFailures = true
                    }
                }
            }

            if (hasFailures) SyncOutcome.Retry else SyncOutcome.Success
        } catch (e: Exception) {
            SyncOutcome.Retry
        }
    }

    private suspend fun recoverSettings(
        identity: Identity,
    ): ResultWithError<Settings, GetSettingsRepositoryError> =
        when (val result = remoteDataSource.get(identity)) {
            is ResultWithError.Success -> {
                val remoteSettings = result.data
                val uiLanguageSetting: LocalSetting<UiLanguage> =
                    when (val setting = remoteSettings.uiLanguage) {
                        is RemoteSetting.Valid -> LocalSetting(
                            value = setting.value,
                            localVersion = setting.serverVersion,
                            syncedVersion = setting.serverVersion,
                            serverVersion = setting.serverVersion,
                            modifiedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED,
                        )

                        is RemoteSetting.Missing -> LocalSetting(
                            value = UiLanguage.English,
                            localVersion = 1,
                            syncedVersion = 0,
                            serverVersion = 0,
                            modifiedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING,
                        )

                        is RemoteSetting.InvalidValue -> LocalSetting(
                            value = UiLanguage.English,
                            localVersion = setting.serverVersion,
                            syncedVersion = setting.serverVersion,
                            serverVersion = setting.serverVersion,
                            modifiedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED,
                        )
                    }
                val localSettings = LocalSettings(
                    uiLanguage = uiLanguageSetting,
                )
                val entities = localSettings.toSettingEntities(identity.userId)
                entities.forEach { entity ->
                    when (localDataSource.update(entity)) {
                        is ResultWithError.Failure -> {
                            // todo it's not a transaction, some settings can be updated
                            return ResultWithError.Failure(GetSettingsRepositoryError.SettingsEmpty)
                        }

                        is ResultWithError.Success -> Unit
                    }
                }
                ResultWithError.Success(localSettings.toDomain())
            }

            is ResultWithError.Failure -> createDefaultSettings(identity.userId)
        }

    private suspend fun createDefaultSettings(
        userId: UserId,
    ): ResultWithError<Settings, GetSettingsRepositoryError> {
        val defaultEntity = createDefaultEntity(
            userId = userId,
            key = SettingKey.UI_LANGUAGE.key,
            defaultValue = UiLanguage.English::class.simpleName ?: "English",
        )
        return when (localDataSource.update(defaultEntity)) {
            is ResultWithError.Success -> {
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsResetToDefaults)
            }

            is ResultWithError.Failure ->
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsEmpty)
        }
    }
}

private fun mapServerValueToLocalValue(
    settingKey: SettingKey,
    serverValue: String,
    fallbackValue: String,
): String = when (settingKey) {
    SettingKey.UI_LANGUAGE -> serverValue.toUiLanguageOrNull()?.toStorageValue()
    SettingKey.THEME,
    SettingKey.NOTIFICATIONS,
    -> error("Setting with key $settingKey validation is not implemented")
} ?: fallbackValue

private fun createDefaultEntity(userId: UserId, key: String, defaultValue: String): SettingEntity =
    SettingEntity(
        userId = userId.id.toString(),
        key = key,
        value = defaultValue,
        localVersion = 0,
        syncedVersion = 0,
        serverVersion = 0,
        modifiedAt = System.currentTimeMillis(),
        syncStatus = SyncStatus.PENDING,
    )
