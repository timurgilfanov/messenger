package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.ResultWithError
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

    override suspend fun updateSetting(
        entity: SettingEntity,
    ): ResultWithError<Unit, UpdateSettingError> {
        settings.update { map ->
            map + (Pair(entity.userId, entity.key) to entity)
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
