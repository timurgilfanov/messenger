package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

data class LocalSettings(val uiLanguage: LocalSetting<UiLanguage>) {
    fun toDomain(): Settings = Settings(uiLanguage = uiLanguage.value)

    fun toSettingEntities(userId: UserId): List<SettingEntity> = listOf(
        SettingEntity(
            userId = userId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = uiLanguage.value.toStorageValue(),
            localVersion = uiLanguage.localVersion,
            syncedVersion = uiLanguage.syncedVersion,
            serverVersion = uiLanguage.serverVersion,
            modifiedAt = uiLanguage.modifiedAt,
            syncStatus = uiLanguage.syncStatus,
        ),
    )

    companion object {
        fun fromEntities(entities: List<SettingEntity>): LocalSettings {
            val uiLanguageEntity = entities.find { it.key == SettingKey.UI_LANGUAGE.key }

            val uiLanguage: LocalSetting<UiLanguage> = if (uiLanguageEntity != null) {
                LocalSetting(
                    value = uiLanguageEntity.value.toUiLanguageOrDefault(UiLanguage.English),
                    localVersion = uiLanguageEntity.localVersion,
                    syncedVersion = uiLanguageEntity.syncedVersion,
                    serverVersion = uiLanguageEntity.serverVersion,
                    modifiedAt = uiLanguageEntity.modifiedAt,
                    syncStatus = uiLanguageEntity.syncStatus,
                )
            } else {
                LocalSetting(
                    value = UiLanguage.English,
                    localVersion = 1,
                    syncedVersion = 0,
                    serverVersion = 0,
                    modifiedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING,
                )
            }

            return LocalSettings(uiLanguage = uiLanguage)
        }
    }
}
