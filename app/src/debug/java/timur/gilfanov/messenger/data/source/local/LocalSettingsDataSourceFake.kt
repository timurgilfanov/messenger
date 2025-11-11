package timur.gilfanov.messenger.data.source.local

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceFake : LocalSettingsDataSource {

    private val settings = MutableStateFlow<Map<Pair<String, String>, SettingEntity>>(emptyMap())

    override fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>> {
        val userIdString = userId.id.toString()
        return settings.map { map ->
            map.values.filter { it.userId == userIdString }
        }
    }

    override suspend fun getSetting(
        userId: UserId,
        key: String,
    ): ResultWithError<SettingEntity, GetSettingError> {
        val userIdString = userId.id.toString()
        val entity = settings.value[Pair(userIdString, key)]
        return if (entity != null) {
            ResultWithError.Success(entity)
        } else {
            ResultWithError.Failure(GetSettingError.SettingNotFound)
        }
    }

    override suspend fun update(entity: SettingEntity): ResultWithError<Unit, UpdateSettingError> {
        settings.update { map ->
            map + (Pair(entity.userId, entity.key) to entity)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun update(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingError> {
        val userIdString = userId.id.toString()
        val entities = settings.value.values.filter { it.userId == userIdString }

        if (entities.isEmpty()) {
            return ResultWithError.Failure(UpdateSettingError.SettingsNotFound)
        }

        val domainSettings = entities.toSettings()
        val transformedSettings = transform(domainSettings)
        val transformedEntities = transformedSettings.toSettingEntities(userId)

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
                        syncStatus = SyncStatus.PENDING,
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

    override suspend fun getUnsyncedSettings():
        ResultWithError<List<SettingEntity>, GetUnsyncedSettingsError> {
        val unsyncedSettings = settings.value.values.filter {
            it.localVersion > it.syncedVersion
        }
        return ResultWithError.Success(unsyncedSettings)
    }

    fun clear() {
        settings.value = emptyMap()
    }
}

private fun List<SettingEntity>.toSettings(): Settings {
    val uiLanguageEntity = find { it.key == SettingKey.UI_LANGUAGE.key }
    val uiLanguage = when (uiLanguageEntity?.value) {
        "German" -> UiLanguage.German
        else -> UiLanguage.English
    }

    val lastModifiedAt = maxOfOrNull { it.modifiedAt } ?: 0L
    val allSynced = all { it.localVersion == it.syncedVersion }
    val lastSyncedAt = if (allSynced && isNotEmpty()) {
        lastModifiedAt
    } else {
        null
    }

    val metadata = SettingsMetadata(
        isDefault = isEmpty(),
        lastModifiedAt = Instant.fromEpochMilliseconds(lastModifiedAt),
        lastSyncedAt = lastSyncedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    return Settings(
        uiLanguage = uiLanguage,
        metadata = metadata,
    )
}

// TODO Reuse this function from production code
private fun Settings.toSettingEntities(userId: UserId): List<SettingEntity> {
    val uiLanguageValue = when (uiLanguage) {
        UiLanguage.English -> "English"
        UiLanguage.German -> "German"
    }

    return listOf(
        SettingEntity(
            userId = userId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = uiLanguageValue,
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        ),
    )
}
