package timur.gilfanov.messenger.data.source.local

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

class LocalSettingsDataSourceFake(
    private val defaultSettings: Settings = Settings(uiLanguage = UiLanguage.English),
) : LocalSettingsDataSource {

    private val settings = MutableStateFlow<Map<Pair<String, String>, SettingEntity>>(emptyMap())

    private var getSettingError: GetSettingError? = null
    private var getUnsyncedError: GetUnsyncedSettingsError? = null
    private var upsertError: UpsertSettingError? = null
    private var transformError: TransformSettingError? = null
    private var observeError: GetSettingsLocalDataSourceError? = null

    fun setGetSettingBehavior(error: GetSettingError?) {
        getSettingError = error
    }

    fun setGetUnsyncedBehavior(error: GetUnsyncedSettingsError?) {
        getUnsyncedError = error
    }

    fun setUpsertBehavior(error: UpsertSettingError?) {
        upsertError = error
    }

    fun setTransformBehavior(error: TransformSettingError?) {
        transformError = error
    }

    fun setObserveBehavior(error: GetSettingsLocalDataSourceError?) {
        observeError = error
    }

    override fun observe(
        userId: UserId,
    ): Flow<ResultWithError<LocalSettings, GetSettingsLocalDataSourceError>> {
        observeError?.let { return flowOf(ResultWithError.Failure(it)) }

        val userIdString = userId.id.toString()
        return settings.map { map ->
            val entities = map.values.filter { it.userId == userIdString }
            if (entities.isEmpty()) {
                ResultWithError.Failure(GetSettingsLocalDataSourceError.NoSettings)
            } else {
                ResultWithError.Success(LocalSettings.fromEntities(entities, defaultSettings))
            }
        }
    }

    override suspend fun getSetting(
        userId: UserId,
        key: SettingKey,
    ): ResultWithError<TypedLocalSetting, GetSettingError> {
        getSettingError?.let { return ResultWithError.Failure(it) }

        val userIdString = userId.id.toString()
        val entity = settings.value[Pair(userIdString, key.key)]
        return if (entity != null) {
            ResultWithError.Success(entity.toTypedLocalSetting(defaultSettings))
        } else {
            ResultWithError.Failure(GetSettingError.SettingNotFound)
        }
    }

    override suspend fun upsert(
        userId: UserId,
        setting: TypedLocalSetting,
    ): ResultWithError<Unit, UpsertSettingError> {
        upsertError?.let { return ResultWithError.Failure(it) }

        val entity = setting.toSettingEntity(userId)
        settings.update { map ->
            map + (Pair(entity.userId, entity.key) to entity)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun transform(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, TransformSettingError> {
        transformError?.let { return ResultWithError.Failure(it) }

        val userIdString = userId.id.toString()
        val entities = settings.value.values.filter { it.userId == userIdString }

        if (entities.isEmpty()) {
            return ResultWithError.Failure(TransformSettingError.SettingsNotFound)
        }

        val localSettings = LocalSettings.fromEntities(entities, defaultSettings)
        val transformedLocalSettings = transform(localSettings)
        val transformedEntities = transformedLocalSettings.toSettingEntities(userId)

        val now = System.currentTimeMillis()
        settings.update { map ->
            var updatedMap = map
            transformedEntities.forEach { updated ->
                val initial = entities.find { it.key == updated.key }
                val entity = if (initial != null && updated.value != initial.value) {
                    updated.copy(
                        localVersion = initial.localVersion + 1,
                        modifiedAt = now,
                        serverVersion = initial.serverVersion,
                        syncedVersion = initial.syncedVersion,
                    )
                } else if (initial == null) {
                    updated
                } else {
                    initial
                }
                updatedMap = updatedMap + (Pair(entity.userId, entity.key) to entity)
            }
            updatedMap
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun getUnsyncedSettings(
        userId: UserId,
    ): ResultWithError<List<TypedLocalSetting>, GetUnsyncedSettingsError> {
        getUnsyncedError?.let { return ResultWithError.Failure(it) }

        val userIdString = userId.id.toString()
        val unsyncedEntities = settings.value.values.filter {
            it.userId == userIdString && it.localVersion > it.syncedVersion
        }
        val typedSettings = unsyncedEntities.map { it.toTypedLocalSetting(defaultSettings) }
        return ResultWithError.Success(typedSettings)
    }

    override suspend fun upsert(
        userId: UserId,
        settings: List<TypedLocalSetting>,
    ): ResultWithError<Unit, UpsertSettingError> {
        upsertError?.let { return ResultWithError.Failure(it) }

        val entities = settings.map { it.toSettingEntity(userId) }
        this.settings.update { map ->
            var updatedMap = map
            entities.forEach { entity ->
                updatedMap = updatedMap + (Pair(entity.userId, entity.key) to entity)
            }
            updatedMap
        }
        return ResultWithError.Success(Unit)
    }

    fun clear() {
        settings.value = emptyMap()
    }
}

private fun SettingEntity.toTypedLocalSetting(defaults: Settings): TypedLocalSetting =
    when (SettingKey.fromKey(this.key)) {
        SettingKey.UI_LANGUAGE -> {
            TypedLocalSetting.UiLanguage(
                setting = LocalSetting(
                    value = this.value.toUiLanguageOrDefault(defaults.uiLanguage),
                    localVersion = this.localVersion,
                    syncedVersion = this.syncedVersion,
                    serverVersion = this.serverVersion,
                    modifiedAt = Instant.fromEpochMilliseconds(this.modifiedAt),
                ),
            )
        }
        SettingKey.THEME -> throw NotImplementedError("${this.key} is not supported")
        SettingKey.NOTIFICATIONS -> throw NotImplementedError("${this.key} is not supported")
        null -> error("Unknown setting key: ${this.key}")
    }

private fun TypedLocalSetting.toSettingEntity(userId: UserId): SettingEntity = when (this) {
    is TypedLocalSetting.UiLanguage -> SettingEntity(
        userId = userId.id.toString(),
        key = this.key.key,
        value = this.setting.value.toStorageValue(),
        localVersion = this.setting.localVersion,
        syncedVersion = this.setting.syncedVersion,
        serverVersion = this.setting.serverVersion,
        modifiedAt = this.setting.modifiedAt.toEpochMilliseconds(),
    )
}
