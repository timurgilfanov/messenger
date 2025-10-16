package timur.gilfanov.messenger.data.repository

import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.data.source.local.GetSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.UpdateSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.toSettingsChangeBackupError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsState
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ApplyRemoteSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError
import timur.gilfanov.messenger.util.Logger

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
 *         currentLocal.metadata.state == SettingsState.LOCAL_MODIFIED &&
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
class SettingsRepositoryImpl(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
    private val logger: Logger,
) : SettingsRepository {

    companion object {
        private const val TAG = "SettingsRepository"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        localDataSource.observeSettings(identity.userId)
            .flatMapLatest { result ->
                result.fold(
                    onSuccess = { settings ->
                        flowOf(Success(settings))
                    },
                    onFailure = { error ->
                        when (error) {
                            GetSettingsLocalDataSourceError.SettingsNotFound -> {
                                flow {
                                    val recoveryResult = performRecovery(identity)
                                    emit(recoveryResult)
                                }
                            }

                            is GetSettingsLocalDataSourceError.LocalDataSource -> {
                                flowOf(
                                    Failure<Settings, GetSettingsRepositoryError>(
                                        GetSettingsRepositoryError.SettingsEmpty,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            .distinctUntilChanged()

    private suspend fun performRecovery(
        identity: Identity,
    ): ResultWithError<Settings, GetSettingsRepositoryError> =
        remoteDataSource.getSettings(identity).fold(
            onSuccess = { remoteSettings ->
                val currentLocal = localDataSource.observeSettings(identity.userId).first()
                    .fold(
                        onSuccess = { it },
                        onFailure = { null },
                    )

                val lastSyncedAt = currentLocal?.metadata?.lastSyncedAt
                    ?: Instant.fromEpochMilliseconds(0)

                if (currentLocal != null &&
                    currentLocal.metadata.state == SettingsState.MODIFIED &&
                    remoteSettings.metadata.lastModifiedAt > lastSyncedAt
                ) {
                    // TODO We can try to resolve conflict here:
                    //  - are differences affect each others?
                    //  - are touched settings global for the user or device-level and remote is just a backup?
                    //  If conflict can't be resolved propagate it to Use Case level with or without merge
                    Failure(
                        GetSettingsRepositoryError.SettingsConflict(
                            localSettings = currentLocal,
                            remoteSettings = remoteSettings,
                        ),
                    )
                } else {
                    localDataSource.insertSettings(
                        userId = identity.userId,
                        settings = remoteSettings,
                    ).foldWithErrorMapping(
                        onSuccess = { Success(remoteSettings) },
                        onFailure = { GetSettingsRepositoryError.SettingsEmpty },
                    )
                }
            },
            onFailure = {
                localDataSource.resetSettings(identity.userId).fold(
                    onSuccess = { Failure(GetSettingsRepositoryError.SettingsResetToDefaults) },
                    onFailure = { Failure(GetSettingsRepositoryError.SettingsEmpty) },
                )
            },
        )

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> =
        localDataSource.updateSettings(identity.userId) { settings ->
            settings.copy(uiLanguage = language)
        }.fold(
            onSuccess = {
                remoteDataSource.changeUiLanguage(identity, language).bimap(
                    onSuccess = {
                        logger.d(TAG, "Language change backed up successfully")
                    },
                    onFailure = { remoteError ->
                        ChangeLanguageRepositoryError.Backup(
                            remoteError.toSettingsChangeBackupError(),
                        )
                    },
                )
            },
            onFailure = { localError ->
                when (localError) {
                    UpdateSettingsLocalDataSourceError.SettingsNotFound -> {
                        performRecovery(identity).foldWithErrorMapping(
                            onSuccess = { changeUiLanguage(identity, language) },
                            onFailure = { error ->
                                when (error) {
                                    GetSettingsRepositoryError.SettingsResetToDefaults ->
                                        ChangeLanguageRepositoryError.SettingsResetToDefaults

                                    GetSettingsRepositoryError.SettingsEmpty ->
                                        ChangeLanguageRepositoryError.SettingsEmpty

                                    is GetSettingsRepositoryError.SettingsConflict ->
                                        ChangeLanguageRepositoryError.SettingsConflict(
                                            localSettings = error.localSettings,
                                            remoteSettings = error.remoteSettings,
                                        )
                                }
                            },
                        )
                    }

                    is UpdateSettingsLocalDataSourceError.TransformError -> Failure(
                        ChangeLanguageRepositoryError.LanguageNotChanged(transient = false),
                    )

                    is UpdateSettingsLocalDataSourceError.GetSettingsLocalDataSource -> Failure(
                        ChangeLanguageRepositoryError.LanguageNotChanged(
                            transient = when (localError.error) {
                                is LocalDataSourceErrorV2.DeserializationError -> false
                                is LocalDataSourceErrorV2.ReadError -> true
                            },
                        ),
                    )

                    is UpdateSettingsLocalDataSourceError.UpdateSettingsLocalDataSource -> Failure(
                        ChangeLanguageRepositoryError.LanguageNotChanged(
                            transient = when (localError.error) {
                                is LocalDataSourceErrorV2.SerializationError -> false
                                is LocalDataSourceErrorV2.WriteError -> true
                            },
                        ),
                    )
                }
            },
        )

    override suspend fun applyRemoteSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, ApplyRemoteSettingsRepositoryError> = localDataSource.insertSettings(
        userId = identity.userId,
        settings = settings,
    ).foldWithErrorMapping(
        onSuccess = { Success(Unit) },
        onFailure = {
            when (it) {
                is LocalDataSourceErrorV2.SerializationError ->
                    ApplyRemoteSettingsRepositoryError.NotTransient
                is LocalDataSourceErrorV2.WriteError -> ApplyRemoteSettingsRepositoryError.Transient
            }
        },
    )

    override suspend fun syncLocalToRemote(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, SyncLocalToRemoteRepositoryError> =
        remoteDataSource.updateSettings(identity, settings).bimap(
            onSuccess = {
                localDataSource.insertSettings(identity.userId, settings).fold(
                    onSuccess = {},
                    onFailure = {},
                )
            },
            onFailure = { SyncLocalToRemoteRepositoryError.SettingsNotSynced },
        )
}
