package timur.gilfanov.messenger.data.source.local

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDao
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceImpl @Inject constructor(private val settingsDao: SettingsDao) :
    LocalSettingsDataSource {

    override fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>> =
        settingsDao.observeAllByUser(userId.id.toString())

    override suspend fun getSetting(userId: UserId, key: String): SettingEntity? =
        settingsDao.get(userId.id.toString(), key)

    override suspend fun updateSetting(entity: SettingEntity) {
        val existing = settingsDao.get(entity.userId, entity.key)
        if (existing == null) {
            settingsDao.insert(entity)
        } else {
            settingsDao.update(entity)
        }
    }

    override suspend fun getUnsyncedSettings(): List<SettingEntity> = settingsDao.getUnsynced()

    companion object {
        fun createDefaultEntity(userId: UserId, key: String, defaultValue: String): SettingEntity =
            SettingEntity(
                userId = userId.id.toString(),
                key = key,
                value = defaultValue,
                localVersion = 0,
                syncedVersion = 0,
                serverVersion = 0,
                modifiedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
            )
    }
}
