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
@Suppress("TooManyFunctions", "LargeClass")
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
                        handleObserveFailure(identity, error)
                    },
                )
            }
            .distinctUntilChanged()

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
                handleLanguageTransformError(identity, language, error)
            },
        )
    }

    private suspend fun handleLanguageTransformError(
        identity: Identity,
        language: UiLanguage,
        error: TransformSettingError,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = when (error) {
        TransformSettingError.SettingsNotFound -> {
            logger.w(
                TAG,
                "Settings not found locally while changing UI language for user " +
                    identity.userId,
            )
            recoverAndRetryLanguageChange(identity, language)
        }

        is TransformSettingError.UnknownError -> {
            val mapped = ChangeLanguageRepositoryError.UnknownError(error.cause)
            logErrorMapping("changeUiLanguage:transform", error, mapped, error.cause)
            ResultWithError.Failure(mapped)
        }

        TransformSettingError.AccessDenied -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.AccessDenied
            logErrorMapping("changeUiLanguage:transform", error, mapped)
            ResultWithError.Failure(mapped)
        }

        TransformSettingError.ConcurrentModificationError,
        TransformSettingError.DiskIOError,
        -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.TemporarilyUnavailable
            logErrorMapping("changeUiLanguage:transform", error, mapped)
            ResultWithError.Failure(mapped)
        }

        TransformSettingError.DatabaseCorrupted -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.DataCorruption
            logErrorMapping("changeUiLanguage:transform", error, mapped)
            ResultWithError.Failure(mapped)
        }

        TransformSettingError.ReadOnlyDatabase -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.ReadOnly
            logErrorMapping("changeUiLanguage:transform", error, mapped)
            ResultWithError.Failure(mapped)
        }

        TransformSettingError.StorageFull -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.InsufficientStorage
            logErrorMapping("changeUiLanguage:transform", error, mapped)
            ResultWithError.Failure(mapped)
        }
    }

    private suspend fun recoverAndRetryLanguageChange(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = recoverSettings(identity).fold(
        onSuccess = {
            changeUiLanguage(identity, language)
        },
        onFailure = { recoverError ->
            handleRecoverSettingsError(identity, language, recoverError)
        },
    )

    private suspend fun handleRecoverSettingsError(
        identity: Identity,
        language: UiLanguage,
        error: GetSettingsRepositoryError,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = when (error) {
        GetSettingsRepositoryError.SettingsResetToDefaults -> {
            logger.w(
                TAG,
                "Settings reset to defaults during language change, " +
                    "retrying for user ${identity.userId}",
            )
            changeUiLanguage(identity, language)
        }

        is GetSettingsRepositoryError.UnknownError -> {
            val mapped = ChangeLanguageRepositoryError.UnknownError(error.cause)
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped, error.cause)
            ResultWithError.Failure(mapped)
        }

        GetSettingsRepositoryError.Recoverable.AccessDenied -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.AccessDenied
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped)
            ResultWithError.Failure(mapped)
        }

        GetSettingsRepositoryError.Recoverable.DataCorruption -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.DataCorruption
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped)
            ResultWithError.Failure(mapped)
        }

        GetSettingsRepositoryError.Recoverable.InsufficientStorage -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.InsufficientStorage
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped)
            ResultWithError.Failure(mapped)
        }

        GetSettingsRepositoryError.Recoverable.ReadOnly -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.ReadOnly
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped)
            ResultWithError.Failure(mapped)
        }

        GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable -> {
            val mapped = ChangeLanguageRepositoryError.Recoverable.TemporarilyUnavailable
            logErrorMapping("changeUiLanguage:recoverSettings", error, mapped)
            ResultWithError.Failure(mapped)
        }
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

    private fun logErrorMapping(
        context: String,
        source: Any,
        mapped: Any,
        cause: Throwable? = null,
    ) {
        if (cause != null) {
            logger.e(
                TAG,
                "Error mapped at $context: source=$source mapped=$mapped",
                cause,
            )
        } else {
            logger.e(
                TAG,
                "Error mapped at $context: source=$source mapped=$mapped",
            )
        }
    }

    private suspend fun handleObserveFailure(
        identity: Identity,
        error: GetSettingsLocalDataSourceError,
    ): ResultWithError<Settings, GetSettingsRepositoryError> = when (error) {
        GetSettingsLocalDataSourceError.NoSettings -> recoverSettings(identity)

        GetSettingsLocalDataSourceError.Recoverable.AccessDenied ->
            logObserveFailure(
                "Access denied while observing settings",
                error,
                GetSettingsRepositoryError.Recoverable.AccessDenied,
            )

        GetSettingsLocalDataSourceError.Recoverable.DataCorruption ->
            logObserveFailure(
                "Database corruption while observing settings",
                error,
                GetSettingsRepositoryError.Recoverable.DataCorruption,
            )

        GetSettingsLocalDataSourceError.Recoverable.InsufficientStorage ->
            logObserveFailure(
                "Insufficient storage while observing settings",
                error,
                GetSettingsRepositoryError.Recoverable.InsufficientStorage,
            )

        GetSettingsLocalDataSourceError.Recoverable.ReadOnly ->
            logObserveFailure(
                "Read-only database while observing settings",
                error,
                GetSettingsRepositoryError.Recoverable.ReadOnly,
            )

        GetSettingsLocalDataSourceError.Recoverable.TemporarilyUnavailable ->
            logObserveFailure(
                "Transient error while observing settings after retries",
                error,
                GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable,
            )

        is GetSettingsLocalDataSourceError.UnknownError ->
            logObserveFailure(
                "Unknown error while observing settings",
                error,
                GetSettingsRepositoryError.UnknownError(error.cause),
                error.cause,
            )
    }

    private fun logObserveFailure(
        message: String,
        error: GetSettingsLocalDataSourceError,
        mapped: GetSettingsRepositoryError,
        cause: Throwable? = null,
    ): ResultWithError<Settings, GetSettingsRepositoryError> {
        if (cause != null) {
            logger.e(TAG, "$message: $error", cause)
        } else {
            logger.e(TAG, "$message: $error")
        }
        return ResultWithError.Failure(mapped)
    }

    @Suppress("ReturnCount")
    override suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> {
        val typedSetting = localDataSource.getSetting(identity.userId, key).fold(
            onSuccess = { it },
            onFailure = { error ->
                val mapped = error.toSyncError(logger, TAG, "syncSetting:getSetting")
                return ResultWithError.Failure(mapped)
            },
        )

        if (!typedSetting.requiresSync()) {
            return ResultWithError.Success(Unit)
        }

        val request = typedSetting.toSyncRequest(identity)

        return remoteDataSource.syncSingleSetting(request).fold(
            onSuccess = { syncResult ->
                handleSingleSyncResult(identity, key, typedSetting, syncResult)
            },
            onFailure = { error ->
                markSyncFailed(
                    identity.userId,
                    listOf(typedSetting.markFailed()),
                    "Failed to mark setting ${typedSetting.key.key} sync as FAILED",
                )
                val mappedRemoteError = error.toRepositoryError()
                logErrorMapping("syncSetting:remote", error, mappedRemoteError)
                ResultWithError.Failure(
                    SyncSettingRepositoryError.RemoteSyncFailed(mappedRemoteError),
                )
            },
        )
    }

    private suspend fun handleSingleSyncResult(
        identity: Identity,
        key: SettingKey,
        typedSetting: TypedLocalSetting,
        syncResult: SyncResult,
    ): ResultWithError<Unit, SyncSettingRepositoryError> = when (syncResult) {
        is SyncResult.Success -> upsertSingleSyncResult(
            identity.userId,
            typedSetting.markLocalSync(syncResult.newVersion),
            "syncSetting:upsertSuccess",
        )

        is SyncResult.Conflict -> handleSingleSyncConflict(
            identity,
            key,
            typedSetting,
            syncResult,
        )
    }

    private suspend fun handleSingleSyncConflict(
        identity: Identity,
        key: SettingKey,
        typedSetting: TypedLocalSetting,
        syncResult: SyncResult.Conflict,
    ): ResultWithError<Unit, SyncSettingRepositoryError> {
        if (typedSetting.setting.modifiedAt >= syncResult.serverModifiedAt) {
            return upsertSingleSyncResult(
                identity.userId,
                typedSetting.markLocalSync(syncResult.newVersion),
                "syncSetting:upsertConflictLocalNewer",
            )
        }

        val resolution = typedSetting.acceptServerState(syncResult)
        return localDataSource.upsert(
            identity.userId,
            resolution.updatedSetting,
        ).foldWithErrorMapping(
            onSuccess = {
                conflictEvents.emit(
                    resolution.toConflictEvent(
                        key = key,
                        serverValue = syncResult.serverValue,
                        serverModifiedAt = syncResult.serverModifiedAt,
                    ),
                )
                ResultWithError.Success(Unit)
            },
            onFailure = { error ->
                error.toSyncError(logger, TAG, "syncSetting:upsertConflictServerNewer")
            },
        )
    }

    private suspend fun upsertSingleSyncResult(
        userId: UserId,
        updatedSetting: TypedLocalSetting,
        context: String,
    ): ResultWithError<Unit, SyncSettingRepositoryError> =
        localDataSource.upsert(userId, updatedSetting).foldWithErrorMapping(
            onSuccess = {
                ResultWithError.Success(Unit)
            },
            onFailure = { error ->
                error.toSyncError(logger, TAG, context)
            },
        )

    @Suppress("ReturnCount")
    override suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> {
        val unsyncedSettings = localDataSource.getUnsyncedSettings(identity.userId).fold(
            onSuccess = { it },
            onFailure = { error ->
                val mapped = error.toBatchSyncError(
                    logger,
                    TAG,
                    "syncAllPending:getUnsynced",
                )
                return ResultWithError.Failure(mapped)
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
                handleBatchSyncResults(identity, unsyncedSettings, results)
            },
            onFailure = { error ->
                markSyncFailed(
                    identity.userId,
                    unsyncedSettings.map { it.markFailed() },
                    "Failed to mark settings sync as FAILED",
                )
                val mappedRemoteError = error.toRepositoryError()
                logErrorMapping("syncAllPending:remote", error, mappedRemoteError)
                ResultWithError.Failure(
                    SyncAllSettingsRepositoryError.RemoteSyncFailed(mappedRemoteError),
                )
            },
        )
    }

    private suspend fun handleBatchSyncResults(
        identity: Identity,
        unsyncedSettings: List<TypedLocalSetting>,
        results: Map<String, SyncResult>,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> {
        var firstUpsertError: SyncAllSettingsRepositoryError.LocalStorageError? = null
        results.forEach { (key, result) ->
            val typedSetting = unsyncedSettings.firstOrNull { it.key.key == key }
            if (typedSetting == null) {
                logger.w(TAG, "Unknown setting key during batch sync: $key")
                return@forEach
            }
            val error = when (result) {
                is SyncResult.Success -> upsertBatchSynced(
                    identity.userId,
                    typedSetting.markLocalSync(result.newVersion),
                    "syncAllPending:upsertSuccess",
                )

                is SyncResult.Conflict -> handleBatchConflict(identity, typedSetting, result)
            }
            if (firstUpsertError == null) {
                firstUpsertError = error
            }
        }
        return firstUpsertError?.let { ResultWithError.Failure(it) }
            ?: ResultWithError.Success(Unit)
    }

    private suspend fun handleBatchConflict(
        identity: Identity,
        typedSetting: TypedLocalSetting,
        syncResult: SyncResult.Conflict,
    ): SyncAllSettingsRepositoryError.LocalStorageError? {
        if (typedSetting.setting.modifiedAt >= syncResult.serverModifiedAt) {
            return upsertBatchSynced(
                identity.userId,
                typedSetting.markLocalSync(syncResult.newVersion),
                "syncAllPending:upsertConflictLocalNewer",
            )
        }

        val resolution = typedSetting.acceptServerState(syncResult)
        return localDataSource.upsert(
            identity.userId,
            resolution.updatedSetting,
        ).fold(
            onSuccess = {
                conflictEvents.emit(
                    resolution.toConflictEvent(
                        key = typedSetting.key,
                        serverValue = syncResult.serverValue,
                        serverModifiedAt = syncResult.serverModifiedAt,
                    ),
                )
                null
            },

            onFailure = { error ->
                error.toBatchSyncError(logger, TAG, "syncAllPending:upsertConflictServerNewer")
            },
        )
    }

    private suspend fun upsertBatchSynced(
        userId: UserId,
        updatedSetting: TypedLocalSetting,
        context: String,
    ): SyncAllSettingsRepositoryError.LocalStorageError? =
        localDataSource.upsert(userId, updatedSetting).fold(
            onSuccess = { null },
            onFailure = { error -> error.toBatchSyncError(logger, TAG, context) },
        )

    private suspend fun markSyncFailed(
        userId: UserId,
        settings: List<TypedLocalSetting>,
        failureMessage: String,
    ) {
        localDataSource.upsert(userId, settings).onFailure { error ->
            logger.e(TAG, "$failureMessage caused by $error")
        }
    }

    private suspend fun recoverSettings(
        identity: Identity,
    ): ResultWithError<Settings, GetSettingsRepositoryError> = remoteDataSource.get(identity).fold(
        onSuccess = { remoteSettings ->
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
                        value = defaultSettings.uiLanguage,
                        localVersion = 1,
                        syncedVersion = 0,
                        serverVersion = 0,
                        modifiedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING,
                    )

                    is RemoteSetting.InvalidValue -> LocalSetting(
                        value = defaultSettings.uiLanguage,
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
                onFailure = { error ->
                    error.toGetSettingsRepositoryError("recoverSettings:upsertLocal")
                },
            )
        },

        onFailure = { remoteError ->
            logger.e(TAG, "Remote recovery failed, falling back to defaults: $remoteError")
            upsertDefaultSettings(identity.userId)
        },
    )

    private fun UpsertSettingError.toGetSettingsRepositoryError(
        context: String,
    ): GetSettingsRepositoryError {
        val mapped =
            when (this) {
                UpsertSettingError.AccessDenied ->
                    GetSettingsRepositoryError.Recoverable.AccessDenied

                UpsertSettingError.ConcurrentModificationError,
                UpsertSettingError.DiskIOError,
                ->
                    GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable

                UpsertSettingError.DatabaseCorrupted ->
                    GetSettingsRepositoryError.Recoverable.DataCorruption

                UpsertSettingError.ReadOnlyDatabase ->
                    GetSettingsRepositoryError.Recoverable.ReadOnly

                UpsertSettingError.StorageFull ->
                    GetSettingsRepositoryError.Recoverable.InsufficientStorage

                is UpsertSettingError.UnknownError -> {
                    logger.e(TAG, "Unknown error while recovering settings", this.cause)
                    GetSettingsRepositoryError.UnknownError(this.cause)
                }
            }
        logErrorMapping(context, this, mapped)
        return mapped
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
                ResultWithError.Failure(
                    it.toGetSettingsRepositoryError("upsertDefaultSettings"),
                )
            },
        )
    }
}

private fun TypedLocalSetting.requiresSync(): Boolean =
    setting.localVersion != setting.syncedVersion

private inline fun <reified T : TypedLocalSetting> T.copy(
    transform: (LocalSetting<*>) -> LocalSetting<*>,
): T {
    val updatedSetting = transform(setting)

    @Suppress("UNCHECKED_CAST") // Worth the simplification and safe enough
    return when (this) {
        is TypedLocalSetting.UiLanguage -> TypedLocalSetting.UiLanguage(
            setting = updatedSetting as LocalSetting<UiLanguage>,
        ) as T
    }
}

private fun TypedLocalSetting.markLocalSync(newServerVersion: Int): TypedLocalSetting =
    copy { setting ->
        setting.copy(
            syncedVersion = setting.localVersion,
            serverVersion = newServerVersion,
            syncStatus = SyncStatus.SYNCED,
        )
    }

private fun TypedLocalSetting.markFailed(): TypedLocalSetting = copy { setting ->
    setting.copy(syncStatus = SyncStatus.FAILED)
}

private fun TypedLocalSetting.acceptServerState(
    conflict: SyncResult.Conflict,
): ConflictResolution = when (this) {
    is TypedLocalSetting.UiLanguage -> {
        val localValue = setting.value.toStorageValue()
        val validatedValue = conflict.serverValue.toUiLanguageOrNull() ?: setting.value
        val acceptedValue = validatedValue.toStorageValue()
        ConflictResolution(
            updatedSetting = TypedLocalSetting.UiLanguage(
                setting = setting.copy(
                    value = validatedValue,
                    localVersion = conflict.newVersion,
                    syncedVersion = conflict.newVersion,
                    serverVersion = conflict.newVersion,
                    modifiedAt = conflict.serverModifiedAt,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
            localValue = localValue,
            acceptedValue = acceptedValue,
        )
    }
}

private data class ConflictResolution(
    val updatedSetting: TypedLocalSetting,
    val localValue: String,
    val acceptedValue: String,
)

private fun ConflictResolution.toConflictEvent(
    key: SettingKey,
    serverValue: String,
    serverModifiedAt: Long,
): SettingsConflictEvent = SettingsConflictEvent(
    settingKey = key,
    localValue = localValue,
    serverValue = serverValue,
    acceptedValue = acceptedValue,
    conflictedAt = Instant.fromEpochMilliseconds(serverModifiedAt),
)

private fun GetSettingError.toSyncError(
    logger: Logger,
    tag: String,
    context: String,
): SyncSettingRepositoryError {
    val mapped = when (this) {
        GetSettingError.SettingNotFound -> SyncSettingRepositoryError.SettingNotFound
        GetSettingError.ConcurrentModificationError,
        GetSettingError.DiskIOError,
        -> SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable

        GetSettingError.DatabaseCorrupted -> SyncSettingRepositoryError.LocalStorageError.Corrupted
        GetSettingError.AccessDenied -> SyncSettingRepositoryError.LocalStorageError.AccessDenied
        GetSettingError.ReadOnlyDatabase -> SyncSettingRepositoryError.LocalStorageError.ReadOnly
        is GetSettingError.UnknownError ->
            SyncSettingRepositoryError.LocalStorageError.UnknownError(this.cause)
    }
    logger.e(tag, "Error mapped at $context: source=$this mapped=$mapped")
    return mapped
}

private fun UpsertSettingError.toSyncError(
    logger: Logger,
    tag: String,
    context: String,
): SyncSettingRepositoryError.LocalStorageError {
    val mapped =
        when (this) {
            UpsertSettingError.ConcurrentModificationError,
            UpsertSettingError.DiskIOError,
            -> SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable

            UpsertSettingError.StorageFull ->
                SyncSettingRepositoryError.LocalStorageError.StorageFull

            UpsertSettingError.DatabaseCorrupted ->
                SyncSettingRepositoryError.LocalStorageError.Corrupted

            UpsertSettingError.AccessDenied ->
                SyncSettingRepositoryError.LocalStorageError.AccessDenied

            UpsertSettingError.ReadOnlyDatabase ->
                SyncSettingRepositoryError.LocalStorageError.ReadOnly

            is UpsertSettingError.UnknownError ->
                SyncSettingRepositoryError.LocalStorageError.UnknownError(this.cause)
        }
    logger.e(tag, "Error mapped at $context: source=$this mapped=$mapped")
    return mapped
}

private fun GetUnsyncedSettingsError.toBatchSyncError(
    logger: Logger,
    tag: String,
    context: String,
): SyncAllSettingsRepositoryError.LocalStorageError {
    val mapped =
        when (this) {
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
    logger.e(tag, "Error mapped at $context: source=$this mapped=$mapped")
    return mapped
}

private fun UpsertSettingError.toBatchSyncError(
    logger: Logger,
    tag: String,
    context: String,
): SyncAllSettingsRepositoryError.LocalStorageError {
    val mapped = when (this) {
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
    logger.e(tag, "Error mapped at $context: source=$this mapped=$mapped")
    return mapped
}
