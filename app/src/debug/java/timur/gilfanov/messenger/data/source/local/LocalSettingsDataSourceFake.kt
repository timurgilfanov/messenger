package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceFake : LocalSettingsDataSource {

    private val settings = MutableStateFlow<Map<Pair<String, String>, SettingEntity>>(emptyMap())

    override fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>> {
        val userIdString = userId.id.toString()
        return settings.map { map ->
            map.values.filter { it.userId == userIdString }
        }
    }

    override suspend fun getSetting(userId: UserId, key: String): SettingEntity? {
        val userIdString = userId.id.toString()
        return settings.value[Pair(userIdString, key)]
    }

    override suspend fun updateSetting(entity: SettingEntity) {
        settings.update { map ->
            map + (Pair(entity.userId, entity.key) to entity)
        }
    }

    override suspend fun getUnsyncedSettings(): List<SettingEntity> = settings.value.values.filter {
        it.localVersion > it.syncedVersion
    }

    fun clear() {
        settings.value = emptyMap()
    }
}
