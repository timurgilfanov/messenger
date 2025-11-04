@file:Suppress("ForbiddenComment")

package timur.gilfanov.messenger.data.repository

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
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
import timur.gilfanov.messenger.data.source.local.GetSettingError
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceImpl
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.SettingSyncRequest
import timur.gilfanov.messenger.data.source.remote.SyncResult
import timur.gilfanov.messenger.data.worker.SyncOutcome
import timur.gilfanov.messenger.data.worker.SyncSettingWorker
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ApplyRemoteSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError

// TODO: Perform recovery for empty settings from local data source
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
    private val workManager: WorkManager,
) : SettingsRepository {

    // TODO: Why we need extra buffer capacity?
    private val conflictEvents = MutableSharedFlow<SettingsConflictEvent>(
        extraBufferCapacity = CONFLICT_EVENT_BUFFER_CAPACITY,
    )

    companion object {
        private const val CONFLICT_EVENT_BUFFER_CAPACITY = 10
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val BACKOFF_DELAY_SECONDS = 15L
    }

    override fun observeConflicts(): Flow<SettingsConflictEvent> = conflictEvents.asSharedFlow()

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        localDataSource.observeSettingEntities(identity.userId)
            .map<
                List<SettingEntity>,
                ResultWithError<Settings, GetSettingsRepositoryError>,
                > { entities ->
                // TODO: Should mapping be done in data layer?
                val settings = mapEntitiesToDomain(entities)
                ResultWithError.Success(settings)
            }
            .catch { exception ->
                emit(ResultWithError.Failure(mapToPermanentError(exception)))
            }

    @Suppress("ReturnCount")
    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> {
        val userId = identity.userId
        val key = SettingKey.UI_LANGUAGE.key

        val entity = when (val result = localDataSource.getSetting(userId, key)) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> {
                when (result.error) {
                    GetSettingError.SettingNotFound ->
                        LocalSettingsDataSourceImpl.createDefaultEntity(
                            userId = userId,
                            key = key,
                            defaultValue = UiLanguage.English::class.simpleName ?: "English",
                        )
                    // TODO: Why StorageUnavailable is success?
                    else -> return ResultWithError.Success(Unit)
                }
            }
        }

        val newValue = when (language) {
            is UiLanguage.English -> "English"
            is UiLanguage.German -> "German"
        }

        val updated = entity.copy(
            value = newValue,
            localVersion = entity.localVersion + 1,
            modifiedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )

        when (localDataSource.updateSetting(updated)) {
            is ResultWithError.Success -> {
                scheduleWorkManagerSync(userId, key)
                return ResultWithError.Success(Unit)
            }

            is ResultWithError.Failure -> return ResultWithError.Success(Unit)
        }
    }

    override suspend fun applyRemoteSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, ApplyRemoteSettingsRepositoryError> = ResultWithError.Success(Unit)

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
    suspend fun syncSetting(userId: UserId, key: String): SyncOutcome {
        val entity = when (val result = localDataSource.getSetting(userId, key)) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> return SyncOutcome.Failure
        }

        if (entity.localVersion == entity.syncedVersion) {
            return SyncOutcome.Success
        }

        val request = SettingSyncRequest(
            userId = userId,
            key = key,
            value = entity.value,
            clientVersion = entity.localVersion,
            lastKnownServerVersion = entity.serverVersion,
            modifiedAt = entity.modifiedAt,
        )

        return when (val result = remoteDataSource.syncSingleSetting(request)) {
            is SyncResult.Success -> {
                when (
                    localDataSource.updateSetting(
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
                        localDataSource.updateSetting(
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
                    when (
                        localDataSource.updateSetting(
                            entity.copy(
                                value = result.serverValue,
                                localVersion = result.newVersion,
                                syncedVersion = result.newVersion,
                                serverVersion = result.newVersion,
                                modifiedAt = result.serverModifiedAt,
                                syncStatus = SyncStatus.SYNCED,
                            ),
                        )
                    ) {
                        is ResultWithError.Success -> {
                            val settingKey = SettingKey.fromKey(key)
                            if (settingKey != null) {
                                conflictEvents.emit(
                                    SettingsConflictEvent(
                                        settingKey = settingKey,
                                        yourValue = entity.value,
                                        acceptedValue = result.serverValue,
                                        conflictedAt = Instant.fromEpochMilliseconds(
                                            result.serverModifiedAt,
                                        ),
                                    ),
                                )
                            }
                            SyncOutcome.Success
                        }

                        is ResultWithError.Failure -> SyncOutcome.Retry
                    }
                }
            }

            is SyncResult.Error -> {
                when (
                    localDataSource.updateSetting(
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
                            localDataSource.updateSetting(
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
                                localDataSource.updateSetting(
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
                            when (
                                localDataSource.updateSetting(
                                    entity.copy(
                                        value = result.serverValue,
                                        localVersion = result.newVersion,
                                        syncedVersion = result.newVersion,
                                        serverVersion = result.newVersion,
                                        modifiedAt = result.serverModifiedAt,
                                        syncStatus = SyncStatus.SYNCED,
                                    ),
                                )
                            ) {
                                is ResultWithError.Success -> {
                                    val settingKey = SettingKey.fromKey(key)
                                    if (settingKey != null) {
                                        conflictEvents.emit(
                                            SettingsConflictEvent(
                                                settingKey = settingKey,
                                                yourValue = entity.value,
                                                acceptedValue = result.serverValue,
                                                conflictedAt = Instant.fromEpochMilliseconds(
                                                    result.serverModifiedAt,
                                                ),
                                            ),
                                        )
                                    }
                                }

                                is ResultWithError.Failure -> hasFailures = true
                            }
                        }
                    }

                    is SyncResult.Error -> {
                        when (
                            localDataSource.updateSetting(
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

    private fun mapEntitiesToDomain(entities: List<SettingEntity>): Settings {
        val uiLanguageEntity = entities.find { it.key == SettingKey.UI_LANGUAGE.key }
        val uiLanguage = when (uiLanguageEntity?.value) {
            "German" -> UiLanguage.German
            else -> UiLanguage.English
        }

        val lastModifiedAt = entities.maxOfOrNull { it.modifiedAt } ?: 0L
        val allSynced = entities.all { it.localVersion == it.syncedVersion }
        val lastSyncedAt = if (allSynced && entities.isNotEmpty()) {
            lastModifiedAt
        } else {
            null
        }

        val metadata = SettingsMetadata(
            isDefault = entities.isEmpty(),
            lastModifiedAt = Instant.fromEpochMilliseconds(lastModifiedAt),
            lastSyncedAt = lastSyncedAt?.let { Instant.fromEpochMilliseconds(it) },
        )

        return Settings(
            uiLanguage = uiLanguage,
            metadata = metadata,
        )
    }

    private fun mapToPermanentError(exception: Throwable): GetSettingsRepositoryError =
        when (exception) {
            is SQLiteFullException -> GetSettingsRepositoryError.Recoverable.InsufficientStorage
            is SQLiteDatabaseCorruptException,
            is IllegalStateException,
            -> GetSettingsRepositoryError.Recoverable.DataCorruption
            // TODO: Why data corruption?
            else -> GetSettingsRepositoryError.Recoverable.DataCorruption
        }
}
