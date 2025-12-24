package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.util.Logger

const val TIME_STEP_SECONDS = 1L

/**
 * A fake implementation of [RemoteSettingsDataSource] for testing purposes.
 *
 * Each instance represents a single user's remote data source (user-scoped).
 * User is identified by the Bearer token attached by the auth interceptor,
 * so the fake doesn't need identity information.
 *
 * @param initialSettings The initial settings for this user.
 * @param useRealTime If true, use system time. If false simulates real-time updates by
 * incrementing timestamps on each fetch. Default is false.
 */
class RemoteSettingsDataSourceFake(
    initialSettings: Settings,
    val useRealTime: Boolean = false,
    val logger: Logger = NoOpLogger(),
) : RemoteSettingsDataSource {

    private data class ServerSettingState(
        val value: String,
        val version: Int,
        val modifiedAt: Instant,
    )

    private val settings = MutableStateFlow(initialSettings)

    private val settingVersions = MutableStateFlow(
        mapOf(SettingKey.UI_LANGUAGE.key to 1),
    )

    private var timeCounter: Instant = Instant.fromEpochMilliseconds(0)

    private val now: Instant
        get() = if (useRealTime) {
            Clock.System.now()
        } else {
            timeCounter = timeCounter.plus(TIME_STEP_SECONDS.seconds)
            timeCounter
        }

    private val serverState = MutableStateFlow(
        mapOf(
            SettingKey.UI_LANGUAGE to ServerSettingState(
                value = when (initialSettings.uiLanguage) {
                    UiLanguage.English -> "English"
                    UiLanguage.German -> "German"
                },
                version = 1,
                modifiedAt = now,
            ),
        ),
    )

    override suspend fun get(): ResultWithError<RemoteSettings, RemoteSettingsDataSourceError> {
        val currentSettings = settings.value
        val items = convertSettingsToDTOs(currentSettings)
        val remoteSettings = RemoteSettings.fromItems(logger, items)
        return ResultWithError.Success(remoteSettings)
    }

    private fun convertSettingsToDTOs(settings: Settings): List<RemoteSettingDto> {
        val versionMap = settingVersions.value
        val uiLanguageValue = when (settings.uiLanguage) {
            UiLanguage.English -> "English"
            UiLanguage.German -> "German"
        }

        return listOf(
            RemoteSettingDto(
                key = SettingKey.UI_LANGUAGE.key,
                value = uiLanguageValue,
                version = versionMap[SettingKey.UI_LANGUAGE.key] ?: 1,
            ),
        )
    }

    override suspend fun changeUiLanguage(
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> {
        settings.update { it.copy(uiLanguage = language) }

        settingVersions.update { versionMap ->
            val newVersion = (versionMap[SettingKey.UI_LANGUAGE.key] ?: 0) + 1
            versionMap + (SettingKey.UI_LANGUAGE.key to newVersion)
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun put(
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> {
        val oldSettings = this.settings.value

        this.settings.update { settings }

        settingVersions.update { versionMap ->
            val updatedVersions = versionMap.toMutableMap()

            if (oldSettings.uiLanguage != settings.uiLanguage) {
                val newVersion = (versionMap[SettingKey.UI_LANGUAGE.key] ?: 0) + 1
                updatedVersions[SettingKey.UI_LANGUAGE.key] = newVersion
            }

            updatedVersions
        }

        return ResultWithError.Success(Unit)
    }

    private var customSyncBehavior:
        ((TypedSettingSyncRequest) -> ResultWithError<SyncResult, SyncSingleSettingError>)? = null

    private var perSettingSyncResults:
        Map<SettingKey, ResultWithError<SyncResult, SyncSingleSettingError>>? = null

    override suspend fun syncSingleSetting(
        request: TypedSettingSyncRequest,
    ): ResultWithError<SyncResult, SyncSingleSettingError> {
        val key = when (request) {
            is TypedSettingSyncRequest.UiLanguage -> SettingKey.UI_LANGUAGE
        }

        perSettingSyncResults?.get(key)?.let { return it }

        customSyncBehavior?.let { return it(request) }

        val value = when (request) {
            is TypedSettingSyncRequest.UiLanguage -> when (request.request.value) {
                UiLanguage.English -> "English"
                UiLanguage.German -> "German"
            }
        }

        val serverSetting = serverState.value[key]
        if (serverSetting == null) {
            val newVersion = 1
            serverState.update { state ->
                state + (
                    key to ServerSettingState(
                        value = value,
                        version = newVersion,
                        modifiedAt = request.request.modifiedAt,
                    )
                    )
            }
            return ResultWithError.Success(SyncResult.Success(newVersion = newVersion))
        }

        if (request.request.modifiedAt >= serverSetting.modifiedAt) {
            val newVersion = serverSetting.version + 1
            serverState.update { state ->
                state + (
                    key to ServerSettingState(
                        value = value,
                        version = newVersion,
                        modifiedAt = request.request.modifiedAt,
                    )
                    )
            }
            return ResultWithError.Success(SyncResult.Success(newVersion = newVersion))
        } else {
            return ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = serverSetting.value,
                    serverVersion = serverSetting.version,
                    newVersion = serverSetting.version,
                    serverModifiedAt = serverSetting.modifiedAt,
                ),
            )
        }
    }

    override suspend fun syncBatch(
        requests: List<TypedSettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, SyncBatchError> = ResultWithError.Success(
        requests.associate { request ->
            val key = when (request) {
                is TypedSettingSyncRequest.UiLanguage -> SettingKey.UI_LANGUAGE.key
            }
            when (val result = syncSingleSetting(request)) {
                is ResultWithError.Success -> key to result.data
                is ResultWithError.Failure -> return ResultWithError.Failure(result.error)
            }
        },
    )

    fun setSyncBehavior(
        behavior: (TypedSettingSyncRequest) -> ResultWithError<SyncResult, SyncSingleSettingError>,
    ) {
        customSyncBehavior = behavior
        perSettingSyncResults = null
    }

    fun setPerSettingSyncResults(
        results: Map<SettingKey, ResultWithError<SyncResult, SyncSingleSettingError>>,
    ) {
        perSettingSyncResults = results
    }

    fun resetSyncBehavior() {
        customSyncBehavior = null
        perSettingSyncResults = null
    }
}
