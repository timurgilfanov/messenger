@file:Suppress("ForbiddenComment")

package timur.gilfanov.messenger.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.GetSettingError
import timur.gilfanov.messenger.data.source.local.GetSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.GetUnsyncedSettingsError
import timur.gilfanov.messenger.data.source.local.LocalSetting
import timur.gilfanov.messenger.data.source.local.LocalSettings
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.TransformSettingError
import timur.gilfanov.messenger.data.source.local.TypedLocalSetting
import timur.gilfanov.messenger.data.source.local.UpsertSettingError
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.local.defaultLocalSetting
import timur.gilfanov.messenger.data.source.local.toStorageValue
import timur.gilfanov.messenger.data.source.local.toUiLanguageOrNull
import timur.gilfanov.messenger.data.source.remote.RemoteSetting
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.SyncResult
import timur.gilfanov.messenger.data.source.remote.toRepositoryError
import timur.gilfanov.messenger.data.worker.SyncSettingWorker
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError
import timur.gilfanov.messenger.util.Logger

/**
 * Implementation of [SettingsRepository] that manages user settings with local caching,
 * remote synchronization, and conflict resolution.
 *
 * ## Architecture:
 *
 * **On-Demand Recovery:**
 * - Settings are automatically recovered from the server when local data is missing
 * - Recovery is triggered when [observeSettings] emits NoSettings error
 * - Recovery is also triggered before [changeUiLanguage] if local settings are not found
 * - Falls back to default settings if remote fetch fails
 *
 * **Synchronization:**
 * - Local changes are queued for background sync via WorkManager
 * - Sync uses Last Write Wins (LWW) conflict resolution based on modification timestamps
 * - Conflicts are detected when both local and remote modified the same setting since last sync
 * - Resolved conflicts emit [SettingsConflictEvent] via [observeConflicts]
 *
 * **Error Handling:**
 * - Permanent errors (corruption, permissions) propagate to use case layer
 * - Network failures during sync are handled by WorkManager retry policies
 *
 * @property localDataSource Provides local storage
 * @property remoteDataSource Provides network-based server communication
 * @property workManager Schedules background sync tasks with backoff and constraints
 * @property logger Diagnostic logging for debugging and monitoring
 */
@Suppress("TooManyFunctions")
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
    private val workManager: WorkManager,
    private val logger: Logger,
    private val defaultSettings: Settings,
) : SettingsRepository {

    /**
     * SharedFlow for conflict events emitted during sync operations.
     *
     * Replay cache prevents event loss when conflicts occur during WorkManager sync
     * while no collectors are active (e.g., app backgrounded) and prevents the syncing
     * process from suspending while waiting for collectors. Replayed events are delivered
     * when collectors start observing, ensuring users see conflicts that occurred while
     * the app was in background.
     *
     * Replay cache size of 10 balances memory usage with preserving recent conflict history.
     */
    private val conflictEvents = MutableSharedFlow<SettingsConflictEvent>(
        replay = CONFLICT_EVENT_REPLAY,
    )

    companion object {
        private const val TAG = "SettingsRepository"
        private const val CONFLICT_EVENT_REPLAY = 10
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val BACKOFF_DELAY_SECONDS = 5L
    }

    override fun observeConflicts(): Flow<SettingsConflictEvent> = conflictEvents.asSharedFlow()

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        localDataSource.observe(identity.userId)
            .map { result ->
                result.fold(
                    onSuccess = {
                        ResultWithError.Success(it.toDomain())
                    },
                    onFailure = { error ->
                        when (error) {
                            GetSettingsLocalDataSourceError.NoSettings -> recoverSettings(identity)
                            GetSettingsLocalDataSourceError.Recoverable.AccessDenied -> {
                                logger.e(TAG, "Access denied while observing settings")
                                ResultWithError.Failure(
                                    GetSettingsRepositoryError.Recoverable.AccessDenied,
                                )
                            }
                            GetSettingsLocalDataSourceError.Recoverable.DataCorruption -> {
                                logger.e(TAG, "Database corruption while observing settings")
                                ResultWithError.Failure(
                                    GetSettingsRepositoryError.Recoverable.DataCorruption,
                                )
                            }
                            GetSettingsLocalDataSourceError.Recoverable.InsufficientStorage -> {
                                logger.e(TAG, "Insufficient storage while observing settings")
                                ResultWithError.Failure(
                                    GetSettingsRepositoryError.Recoverable.InsufficientStorage,
                                )
                            }
                            GetSettingsLocalDataSourceError.Recoverable.ReadOnly -> {
                                logger.e(TAG, "Read-only database while observing settings")
                                ResultWithError.Failure(
                                    GetSettingsRepositoryError.Recoverable.ReadOnly,
                                )
                            }
                            GetSettingsLocalDataSourceError.Recoverable.TemporarilyUnavailable -> {
                                logger.e(
                                    TAG,
                                    "Transient error while observing settings after retries",
                                )
                                ResultWithError.Failure(
                                    GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable,
                                )
                            }
                            GetSettingsLocalDataSourceError.Unknown -> {
                                logger.e(TAG, "Unknown error while observing settings")
                                ResultWithError.Failure(GetSettingsRepositoryError.Unknown)
                            }
                        }
                    },
                )
            }
            .distinctUntilChanged()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> {
        val userId = identity.userId
        val key = SettingKey.UI_LANGUAGE.key

        return localDataSource.transform(userId) { localSettings ->
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
                    TransformSettingError.SettingsNotFound -> {
                        recoverSettings(identity).fold(
                            onSuccess = {
                                changeUiLanguage(identity, language)
                            },
                            onFailure = { error ->
                                when (error) {
                                    GetSettingsRepositoryError.Recoverable.AccessDenied ->
                                        ResultWithError.Failure(
                                            ChangeLanguageRepositoryError.Recoverable.AccessDenied,
                                        )
                                    GetSettingsRepositoryError.Recoverable.DataCorruption ->
                                        ResultWithError.Failure(
                                            ChangeLanguageRepositoryError.Recoverable
                                                .DataCorruption,
                                        )
                                    GetSettingsRepositoryError.Recoverable.InsufficientStorage ->
                                        ResultWithError.Failure(
                                            ChangeLanguageRepositoryError.Recoverable
                                                .InsufficientStorage,
                                        )

                                    GetSettingsRepositoryError.Recoverable.ReadOnly,
                                    -> ResultWithError.Failure(
                                        ChangeLanguageRepositoryError.Recoverable.ReadOnly,
                                    )

                                    GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable ->
                                        ResultWithError.Failure(
                                            ChangeLanguageRepositoryError.Recoverable
                                                .TemporarilyUnavailable,
                                        )

                                    GetSettingsRepositoryError.SettingsResetToDefaults ->
                                        changeUiLanguage(identity, language)
                                    GetSettingsRepositoryError.Unknown -> ResultWithError.Failure(
                                        ChangeLanguageRepositoryError.Unknown,
                                    )
                                }
                            },
                        )
                    }

                    TransformSettingError.ConcurrentModificationError,
                    TransformSettingError.DiskIOError,
                    -> ResultWithError.Failure(
                        ChangeLanguageRepositoryError.Recoverable.TemporarilyUnavailable,
                    )
                    TransformSettingError.DatabaseCorrupted -> ResultWithError.Failure(
                        ChangeLanguageRepositoryError.Recoverable.DataCorruption,
                    )
                    TransformSettingError.AccessDenied -> ResultWithError.Failure(
                        ChangeLanguageRepositoryError.Recoverable.AccessDenied,
                    )
                    TransformSettingError.ReadOnlyDatabase -> ResultWithError.Failure(
                        ChangeLanguageRepositoryError.Recoverable.ReadOnly,
                    )
                    TransformSettingError.StorageFull -> ResultWithError.Failure(
                        ChangeLanguageRepositoryError.Recoverable.InsufficientStorage,
                    )
                    is TransformSettingError.UnknownError -> {
                        logger.e(TAG, "Unknown error while changing UI language", error.cause)
                        ResultWithError.Failure(ChangeLanguageRepositoryError.Unknown)
                    }
                }
            },
        )
    }

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
    override suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> {
        val typedSetting = localDataSource.getSetting(identity.userId, key).fold(
            onSuccess = { it },
            onFailure = { error -> return ResultWithError.Failure(error.toSyncError()) },
        )

        if (typedSetting.setting.localVersion == typedSetting.setting.syncedVersion) {
            return ResultWithError.Success(Unit)
        }

        val request = typedSetting.toSyncRequest(identity)

        return remoteDataSource.syncSingleSetting(request).fold(
            onSuccess = { syncResult ->
                when (syncResult) {
                    is SyncResult.Success -> {
                        val updatedTypedSetting = when (typedSetting) {
                            is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                                setting = typedSetting.setting.copy(
                                    syncedVersion = typedSetting.setting.localVersion,
                                    serverVersion = syncResult.newVersion,
                                    syncStatus = SyncStatus.SYNCED,
                                ),
                            )
                        }
                        when (
                            val upsertResult = localDataSource.upsert(
                                identity.userId,
                                updatedTypedSetting,
                            )
                        ) {
                            is ResultWithError.Success -> ResultWithError.Success(Unit)
                            is ResultWithError.Failure -> ResultWithError.Failure(
                                upsertResult.error.toSyncError(),
                            )
                        }
                    }

                    is SyncResult.Conflict -> {
                        if (typedSetting.setting.modifiedAt >= syncResult.serverModifiedAt) {
                            val updatedTypedSetting = when (typedSetting) {
                                is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                                    setting = typedSetting.setting.copy(
                                        syncedVersion = typedSetting.setting.localVersion,
                                        serverVersion = syncResult.newVersion,
                                        syncStatus = SyncStatus.SYNCED,
                                    ),
                                )
                            }
                            when (
                                val upsertResult = localDataSource.upsert(
                                    identity.userId,
                                    updatedTypedSetting,
                                )
                            ) {
                                is ResultWithError.Success -> ResultWithError.Success(Unit)
                                is ResultWithError.Failure -> ResultWithError.Failure(
                                    upsertResult.error.toSyncError(),
                                )
                            }
                        } else {
                            val (updatedTypedSetting, localValueStr, acceptedValueStr) =
                                when (typedSetting) {
                                    is TypedLocalSetting.UiLanguage -> {
                                        val localValueStr =
                                            typedSetting.setting.value.toStorageValue()
                                        val validatedValue =
                                            syncResult.serverValue.toUiLanguageOrNull()
                                                ?: typedSetting.setting.value
                                        val acceptedValueStr = validatedValue.toStorageValue()
                                        Triple(
                                            TypedLocalSetting.UiLanguage(
                                                setting = typedSetting.setting.copy(
                                                    value = validatedValue,
                                                    localVersion = syncResult.newVersion,
                                                    syncedVersion = syncResult.newVersion,
                                                    serverVersion = syncResult.newVersion,
                                                    modifiedAt = syncResult.serverModifiedAt,
                                                    syncStatus = SyncStatus.SYNCED,
                                                ),
                                            ),
                                            localValueStr,
                                            acceptedValueStr,
                                        )
                                    }
                                }

                            when (
                                val upsertResult = localDataSource.upsert(
                                    identity.userId,
                                    updatedTypedSetting,
                                )
                            ) {
                                is ResultWithError.Success -> {
                                    conflictEvents.emit(
                                        SettingsConflictEvent(
                                            settingKey = key,
                                            localValue = localValueStr,
                                            serverValue = syncResult.serverValue,
                                            acceptedValue = acceptedValueStr,
                                            conflictedAt = Instant.fromEpochMilliseconds(
                                                syncResult.serverModifiedAt,
                                            ),
                                        ),
                                    )
                                    ResultWithError.Success(Unit)
                                }

                                is ResultWithError.Failure -> ResultWithError.Failure(
                                    upsertResult.error.toSyncError(),
                                )
                            }
                        }
                    }
                }
            },
            onFailure = { error ->
                val failedTypedSetting = when (typedSetting) {
                    is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                        setting = typedSetting.setting.copy(syncStatus = SyncStatus.FAILED),
                    )
                }
                localDataSource.upsert(
                    identity.userId,
                    failedTypedSetting,
                ).onFailure { error1 ->
                    logger.e(
                        TAG,
                        "Failed to mark setting ${typedSetting.key.key} sync as FAILED caused by $error1",
                    )
                }
                ResultWithError.Failure(
                    SyncSettingRepositoryError.RemoteSyncFailed(error.toRepositoryError()),
                )
            },
        )
    }

    @Suppress(
        "LongMethod",
        "NestedBlockDepth",
        "ReturnCount",
        "CyclomaticComplexMethod",
    )
    override suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<
        Unit,
        SyncAllSettingsRepositoryError,
        > {
        val unsyncedSettings = localDataSource.getUnsyncedSettings(identity.userId).fold(
            onSuccess = { it },
            onFailure = { error ->
                return ResultWithError.Failure(error.toBatchSyncError())
            },
        )

        if (unsyncedSettings.isEmpty()) {
            return ResultWithError.Success(Unit)
        }

        val requests = unsyncedSettings.map { typedSetting ->
            typedSetting.toSyncRequest(identity)
        }

        return remoteDataSource.syncBatch(requests).fold(
            onSuccess = { results ->
                var firstUpsertError: SyncAllSettingsRepositoryError.LocalStorageError? = null
                results.forEach { (key, result) ->
                    val typedSetting = unsyncedSettings.find { it.key.key == key } ?: return@forEach

                    when (result) {
                        is SyncResult.Success -> {
                            val updatedTypedSetting = when (typedSetting) {
                                is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                                    setting = typedSetting.setting.copy(
                                        syncedVersion = typedSetting.setting.localVersion,
                                        serverVersion = result.newVersion,
                                        syncStatus = SyncStatus.SYNCED,
                                    ),
                                )
                            }
                            when (
                                val upsertResult = localDataSource.upsert(
                                    identity.userId,
                                    updatedTypedSetting,
                                )
                            ) {
                                is ResultWithError.Success -> Unit
                                is ResultWithError.Failure -> {
                                    if (firstUpsertError == null) {
                                        firstUpsertError = upsertResult.error.toBatchSyncError()
                                    }
                                }
                            }
                        }

                        is SyncResult.Conflict -> {
                            if (typedSetting.setting.modifiedAt >= result.serverModifiedAt) {
                                val updatedTypedSetting = when (typedSetting) {
                                    is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                                        setting = typedSetting.setting.copy(
                                            syncedVersion = typedSetting.setting.localVersion,
                                            serverVersion = result.newVersion,
                                            syncStatus = SyncStatus.SYNCED,
                                        ),
                                    )
                                }
                                when (
                                    val upsertResult = localDataSource.upsert(
                                        identity.userId,
                                        updatedTypedSetting,
                                    )
                                ) {
                                    is ResultWithError.Success -> Unit
                                    is ResultWithError.Failure -> {
                                        if (firstUpsertError == null) {
                                            firstUpsertError = upsertResult.error.toBatchSyncError()
                                        }
                                    }
                                }
                            } else {
                                val settingKey = SettingKey.fromKey(key)
                                if (settingKey == null) {
                                    logger.w(TAG, "Unknown setting key during batch sync: $key")
                                    return@forEach
                                }

                                val (updatedTypedSetting, localValueStr, acceptedValueStr) =
                                    when (typedSetting) {
                                        is TypedLocalSetting.UiLanguage -> {
                                            val localValueStr =
                                                typedSetting.setting.value.toStorageValue()
                                            val validatedValue =
                                                result.serverValue.toUiLanguageOrNull()
                                                    ?: typedSetting.setting.value
                                            val acceptedValueStr = validatedValue.toStorageValue()
                                            Triple(
                                                TypedLocalSetting.UiLanguage(
                                                    setting = typedSetting.setting.copy(
                                                        value = validatedValue,
                                                        localVersion = result.newVersion,
                                                        syncedVersion = result.newVersion,
                                                        serverVersion = result.newVersion,
                                                        modifiedAt = result.serverModifiedAt,
                                                        syncStatus = SyncStatus.SYNCED,
                                                    ),
                                                ),
                                                localValueStr,
                                                acceptedValueStr,
                                            )
                                        }
                                    }

                                when (
                                    val upsertResult = localDataSource.upsert(
                                        identity.userId,
                                        updatedTypedSetting,
                                    )
                                ) {
                                    is ResultWithError.Success -> {
                                        conflictEvents.emit(
                                            SettingsConflictEvent(
                                                settingKey = settingKey,
                                                localValue = localValueStr,
                                                serverValue = result.serverValue,
                                                acceptedValue = acceptedValueStr,
                                                conflictedAt = Instant.fromEpochMilliseconds(
                                                    result.serverModifiedAt,
                                                ),
                                            ),
                                        )
                                    }

                                    is ResultWithError.Failure -> {
                                        if (firstUpsertError == null) {
                                            firstUpsertError = upsertResult.error.toBatchSyncError()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (firstUpsertError != null) {
                    ResultWithError.Failure(firstUpsertError)
                } else {
                    ResultWithError.Success(Unit)
                }
            },

            onFailure = { error ->
                val failedSettings = unsyncedSettings.map { typedSetting ->
                    when (typedSetting) {
                        is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
                            setting = typedSetting.setting.copy(syncStatus = SyncStatus.FAILED),
                        )
                    }
                }
                localDataSource.upsert(
                    identity.userId,
                    failedSettings,
                ).onFailure { error1 ->
                    logger.e(TAG, "Failed to mark settings sync as FAILED caused by $error1")
                }
                ResultWithError.Failure(
                    SyncAllSettingsRepositoryError.RemoteSyncFailed(error.toRepositoryError()),
                )
            },
        )
    }

    private suspend fun recoverSettings(
        identity: Identity,
    ): ResultWithError<Settings, GetSettingsRepositoryError> = remoteDataSource.get(identity).fold(
        onSuccess = { remoteSettings ->
            val uiLanguageSetting: LocalSetting<UiLanguage> =
                when (val setting = remoteSettings.uiLanguage) {
                    is RemoteSetting.Valid -> LocalSetting(
                        value = setting.value,
                        defaultValue = defaultSettings.uiLanguage,
                        localVersion = setting.serverVersion,
                        syncedVersion = setting.serverVersion,
                        serverVersion = setting.serverVersion,
                        modifiedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED,
                    )

                    is RemoteSetting.Missing -> LocalSetting(
                        value = defaultSettings.uiLanguage,
                        defaultValue = defaultSettings.uiLanguage,
                        localVersion = 1,
                        syncedVersion = 0,
                        serverVersion = 0,
                        modifiedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING,
                    )

                    is RemoteSetting.InvalidValue -> LocalSetting(
                        value = defaultSettings.uiLanguage,
                        defaultValue = defaultSettings.uiLanguage,
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
            val typedSettings = listOf(
                TypedLocalSetting.UiLanguage(setting = uiLanguageSetting),
            )
            localDataSource.upsert(identity.userId, typedSettings).foldWithErrorMapping(
                onSuccess = { ResultWithError.Success(localSettings.toDomain()) },
                onFailure = { error -> error.toGetSettingsRepositoryError() },
            )
        },

        onFailure = { _ -> upsertDefaultSettings(identity.userId) },
    )

    private fun UpsertSettingError.toGetSettingsRepositoryError(): GetSettingsRepositoryError =
        when (this) {
            UpsertSettingError.AccessDenied -> GetSettingsRepositoryError.Recoverable.AccessDenied
            UpsertSettingError.ConcurrentModificationError,
            UpsertSettingError.DiskIOError,
            -> GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable
            UpsertSettingError.DatabaseCorrupted ->
                GetSettingsRepositoryError.Recoverable.DataCorruption
            UpsertSettingError.ReadOnlyDatabase -> GetSettingsRepositoryError.Recoverable.ReadOnly
            UpsertSettingError.StorageFull ->
                GetSettingsRepositoryError.Recoverable.InsufficientStorage
            is UpsertSettingError.UnknownError -> {
                logger.e(TAG, "Unknown error while recovering settings", this.cause)
                GetSettingsRepositoryError.Unknown
            }
        }

    private suspend fun upsertDefaultSettings(
        userId: UserId,
    ): ResultWithError<Settings, GetSettingsRepositoryError> {
        val now = Clock.System.now()
        val defaultLocalSettings = LocalSettings(
            uiLanguage = defaultLocalSetting(defaultSettings.uiLanguage, now),
        )
        val typedSettings = listOf(
            TypedLocalSetting.UiLanguage(setting = defaultLocalSettings.uiLanguage),
        )
        return localDataSource.upsert(userId, typedSettings).fold(
            onSuccess = {
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsResetToDefaults)
            },
            onFailure = {
                ResultWithError.Failure(it.toGetSettingsRepositoryError())
            },
        )
    }
}

private fun GetSettingError.toSyncError(): SyncSettingRepositoryError = when (this) {
    GetSettingError.SettingNotFound -> SyncSettingRepositoryError.SettingNotFound
    GetSettingError.ConcurrentModificationError,
    GetSettingError.DiskIOError,
    -> SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable
    GetSettingError.DatabaseCorrupted -> SyncSettingRepositoryError.LocalStorageError.Corrupted
    GetSettingError.AccessDenied -> SyncSettingRepositoryError.LocalStorageError.AccessDenied
    GetSettingError.ReadOnlyDatabase -> SyncSettingRepositoryError.LocalStorageError.ReadOnly
    is GetSettingError.UnknownError -> SyncSettingRepositoryError.LocalStorageError.UnknownError(
        this.cause,
    )
}

private fun UpsertSettingError.toSyncError(): SyncSettingRepositoryError.LocalStorageError =
    when (this) {
        UpsertSettingError.ConcurrentModificationError,
        UpsertSettingError.DiskIOError,
        -> SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable
        UpsertSettingError.StorageFull -> SyncSettingRepositoryError.LocalStorageError.StorageFull
        UpsertSettingError.DatabaseCorrupted ->
            SyncSettingRepositoryError.LocalStorageError.Corrupted
        UpsertSettingError.AccessDenied -> SyncSettingRepositoryError.LocalStorageError.AccessDenied
        UpsertSettingError.ReadOnlyDatabase -> SyncSettingRepositoryError.LocalStorageError.ReadOnly
        is UpsertSettingError.UnknownError ->
            SyncSettingRepositoryError.LocalStorageError.UnknownError(this.cause)
    }

private fun GetUnsyncedSettingsError.toBatchSyncError() = when (this) {
    GetUnsyncedSettingsError.ConcurrentModificationError,
    GetUnsyncedSettingsError.DiskIOError,
    -> SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable
    GetUnsyncedSettingsError.DatabaseCorrupted ->
        SyncAllSettingsRepositoryError.LocalStorageError.Corrupted
    GetUnsyncedSettingsError.AccessDenied ->
        SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied
    GetUnsyncedSettingsError.ReadOnlyDatabase ->
        SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly
    is GetUnsyncedSettingsError.UnknownError ->
        SyncAllSettingsRepositoryError.LocalStorageError.UnknownError(this.cause)
}

private fun UpsertSettingError.toBatchSyncError() = when (this) {
    UpsertSettingError.ConcurrentModificationError,
    UpsertSettingError.DiskIOError,
    -> SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable
    UpsertSettingError.StorageFull ->
        SyncAllSettingsRepositoryError.LocalStorageError.StorageFull
    UpsertSettingError.DatabaseCorrupted ->
        SyncAllSettingsRepositoryError.LocalStorageError.Corrupted
    UpsertSettingError.AccessDenied ->
        SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied
    UpsertSettingError.ReadOnlyDatabase ->
        SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly
    is UpsertSettingError.UnknownError ->
        SyncAllSettingsRepositoryError.LocalStorageError.UnknownError(this.cause)
}
