@file:Suppress("ForbiddenComment")

package timur.gilfanov.messenger.data.repository

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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.GetSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalSetting
import timur.gilfanov.messenger.data.source.local.LocalSettings
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.TransformSettingError
import timur.gilfanov.messenger.data.source.local.UpsertSettingError
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
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
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
                    localDataSource.upsert(
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
                        localDataSource.upsert(
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
                        localDataSource.upsert(
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
                                    localValue = entity.value,
                                    serverValue = result.serverValue,
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
                    localDataSource.upsert(
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

        val results = remoteDataSource.syncBatch(requests)

        var hasFailures = false
        results.forEach { (key, result) ->
            val entity = unsyncedSettings.find { it.key == key } ?: return@forEach

            when (result) {
                is SyncResult.Success -> {
                    when (
                        localDataSource.upsert(
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
                            localDataSource.upsert(
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
                            localDataSource.upsert(
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
                                        localValue = entity.value,
                                        serverValue = result.serverValue,
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
                        localDataSource.upsert(
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

        return if (hasFailures) SyncOutcome.Retry else SyncOutcome.Success
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
                localDataSource.upsert(entities).foldWithErrorMapping(
                    onSuccess = { ResultWithError.Success(localSettings.toDomain()) },
                    onFailure = { error -> error.toGetSettingsRepositoryError() },
                )
            }

            is ResultWithError.Failure -> upsertDefaultSettings(identity.userId)
        }

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
            uiLanguage = defaultLocalSetting(UiLanguage.English, now),
        )
        val entities = defaultLocalSettings.toSettingEntities(userId)
        return localDataSource.upsert(entities).fold(
            onSuccess = {
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsResetToDefaults)
            },
            onFailure = {
                ResultWithError.Failure(it.toGetSettingsRepositoryError())
            },
        )
    }
}

private fun <T> defaultLocalSetting(value: T, modifiedAt: Instant): LocalSetting<T> = LocalSetting(
    value = value,
    localVersion = 1,
    syncedVersion = 0,
    serverVersion = 0,
    modifiedAt = modifiedAt.toEpochMilliseconds(),
    syncStatus = SyncStatus.PENDING,
)

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
