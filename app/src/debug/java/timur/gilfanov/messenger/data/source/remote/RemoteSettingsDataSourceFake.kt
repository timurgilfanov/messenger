package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

const val TIME_STEP_SECONDS = 1L

/**
 * A fake implementation of [RemoteSettingsDataSource] for testing purposes.
 *
 * While we not implement product [RemoteSettingsDataSource] we can use this fake with
 * [useRealTime] = true with production source set.
 *
 * @param initialSettings The initial settings to populate the data source with.
 * @param useRealTime If true, use system time. If false simulates real-time updates by
 * incrementing timestamps on each fetch. Default is false.
 */

class RemoteSettingsDataSourceFake(
    initialSettings: PersistentMap<UserId, Settings>,
    val useRealTime: Boolean = false,
) : RemoteSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    private var timeCounter: Instant = Instant.fromEpochMilliseconds(0)

    private val now: Instant
        get() = if (useRealTime) {
            Clock.System.now()
        } else {
            timeCounter = timeCounter.plus(TIME_STEP_SECONDS.seconds)
            timeCounter
        }

    override suspend fun get(
        identity: Identity,
    ): ResultWithError<Settings, RemoteUserDataSourceError> {
        val userSettings = settings.value[identity.userId]
        return if (userSettings == null) {
            ResultWithError.Failure(RemoteUserDataSourceError.Authentication.SessionRevoked)
        } else {
            ResultWithError.Success(
                userSettings.copy(
                    metadata = userSettings.metadata.copy(
                        lastSyncedAt = now,
                    ),
                ),
            )
        }
    }

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> {
        settings.update {
            val userSettings = it[identity.userId] ?: return ResultWithError.Failure(
                RemoteUserDataSourceError.Authentication.SessionRevoked,
            )
            it.put(
                identity.userId,
                userSettings.copy(
                    uiLanguage = language,
                    metadata = SettingsMetadata(
                        isDefault = false,
                        lastModifiedAt = now,
                        lastSyncedAt = null,
                    ),
                ),
            )
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun put(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> {
        this.settings.update {
            if (!it.containsKey(identity.userId)) {
                return ResultWithError.Failure(
                    RemoteUserDataSourceError.Authentication.SessionRevoked,
                )
            }
            it.put(
                identity.userId,
                settings.copy(
                    metadata = SettingsMetadata(
                        isDefault = settings.metadata.isDefault,
                        lastModifiedAt = now,
                        lastSyncedAt = null,
                    ),
                ),
            )
        }
        return ResultWithError.Success(Unit)
    }

    private var syncBehavior: (SettingSyncRequest) -> SyncResult = { request ->
        SyncResult.Success(newVersion = request.clientVersion + 1)
    }

    override suspend fun syncSingleSetting(request: SettingSyncRequest): SyncResult =
        syncBehavior(request)

    override suspend fun syncBatch(requests: List<SettingSyncRequest>): Map<String, SyncResult> =
        requests.associate { request ->
            request.key to syncBehavior(request)
        }

    fun setSyncBehavior(behavior: (SettingSyncRequest) -> SyncResult) {
        syncBehavior = behavior
    }

    fun resetSyncBehavior() {
        syncBehavior = { request ->
            SyncResult.Success(newVersion = request.clientVersion + 1)
        }
    }
}
