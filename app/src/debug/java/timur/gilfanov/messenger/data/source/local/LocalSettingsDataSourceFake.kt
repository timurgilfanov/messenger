package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceFake : LocalSettingsDataSource {

    private val settings = MutableStateFlow<Map<Pair<String, String>, SettingEntity>>(emptyMap())

    override fun observe(
        userId: UserId,
    ): Flow<ResultWithError<LocalSettings, GetSettingsLocalDataSourceError>> {
        val userIdString = userId.id.toString()
        return settings.map { map ->
            val entities = map.values.filter { it.userId == userIdString }
            if (entities.isEmpty()) {
                ResultWithError.Failure(GetSettingsLocalDataSourceError.NoSettings)
            } else {
                ResultWithError.Success(LocalSettings.fromEntities(entities))
            }
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

    override suspend fun upsert(entity: SettingEntity): ResultWithError<Unit, UpsertSettingError> {
        settings.update { map ->
            map + (Pair(entity.userId, entity.key) to entity)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun transform(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, TransformSettingError> {
        val userIdString = userId.id.toString()
        val entities = settings.value.values.filter { it.userId == userIdString }

        if (entities.isEmpty()) {
            return ResultWithError.Failure(TransformSettingError.SettingsNotFound)
        }

        val localSettings = LocalSettings.fromEntities(entities)
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

    override suspend fun upsert(
        entities: List<SettingEntity>,
    ): ResultWithError<Unit, UpsertSettingError> {
        settings.update { map ->
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
