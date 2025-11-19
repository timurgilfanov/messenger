package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.util.Logger

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
    val logger: Logger = NoOpLogger(),
) : RemoteSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    private val settingVersions = MutableStateFlow(
        initialSettings.keys.associateWith {
            mapOf(SettingKey.UI_LANGUAGE.key to 1)
        },
    )

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
    ): ResultWithError<RemoteSettings, RemoteUserDataSourceError> {
        val userSettings = settings.value[identity.userId]
        return if (userSettings == null) {
            ResultWithError.Failure(RemoteUserDataSourceError.Authentication.SessionRevoked)
        } else {
            val items = convertSettingsToItems(userSettings, identity.userId)
            val remoteSettings = RemoteSettings.fromItems(logger, items)
            ResultWithError.Success(remoteSettings)
        }
    }

    private fun convertSettingsToItems(
        settings: Settings,
        userId: UserId,
    ): List<RemoteSettingItem> {
        val versionMap = settingVersions.value[userId] ?: emptyMap()
        val uiLanguageValue = when (settings.uiLanguage) {
            UiLanguage.English -> "English"
            UiLanguage.German -> "German"
        }

        return listOf(
            RemoteSettingItem(
                key = SettingKey.UI_LANGUAGE.key,
                value = uiLanguageValue,
                version = versionMap[SettingKey.UI_LANGUAGE.key] ?: 1,
            ),
        )
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
                userSettings.copy(uiLanguage = language),
            )
        }

        settingVersions.update { versionMap ->
            val userVersions = versionMap[identity.userId] ?: emptyMap()
            val newVersion = (userVersions[SettingKey.UI_LANGUAGE.key] ?: 0) + 1
            versionMap +
                (identity.userId to (userVersions + (SettingKey.UI_LANGUAGE.key to newVersion)))
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun put(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> {
        val oldSettings = this.settings.value[identity.userId]

        this.settings.update {
            if (!it.containsKey(identity.userId)) {
                return ResultWithError.Failure(
                    RemoteUserDataSourceError.Authentication.SessionRevoked,
                )
            }
            it.put(identity.userId, settings)
        }

        if (oldSettings != null) {
            settingVersions.update { versionMap ->
                val userVersions = versionMap[identity.userId] ?: emptyMap()
                val updatedVersions = userVersions.toMutableMap()

                if (oldSettings.uiLanguage != settings.uiLanguage) {
                    val newVersion = (userVersions[SettingKey.UI_LANGUAGE.key] ?: 0) + 1
                    updatedVersions[SettingKey.UI_LANGUAGE.key] = newVersion
                }

                versionMap + (identity.userId to updatedVersions)
            }
        }

        return ResultWithError.Success(Unit)
    }

    private var syncBehavior:
        (SettingSyncRequest) -> ResultWithError<SyncResult, SyncSingleSettingError> =
        { request ->
            ResultWithError.Success(SyncResult.Success(newVersion = request.clientVersion + 1))
        }

    override suspend fun syncSingleSetting(
        request: SettingSyncRequest,
    ): ResultWithError<SyncResult, SyncSingleSettingError> = syncBehavior(request)

    override suspend fun syncBatch(
        requests: List<SettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, SyncBatchError> = ResultWithError.Success(
        requests.associate { request ->
            when (val result = syncBehavior(request)) {
                is ResultWithError.Success -> request.key to result.data
                is ResultWithError.Failure -> return ResultWithError.Failure(result.error)
            }
        },
    )

    fun setSyncBehavior(
        behavior: (SettingSyncRequest) -> ResultWithError<SyncResult, SyncSingleSettingError>,
    ) {
        syncBehavior = behavior
    }

    fun resetSyncBehavior() {
        syncBehavior = { request ->
            ResultWithError.Success(SyncResult.Success(newVersion = request.clientVersion + 1))
        }
    }
}
