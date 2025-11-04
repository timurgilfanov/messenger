package timur.gilfanov.messenger.domain.entity.user

import kotlin.time.Instant

data class SettingsConflictEvent(
    val settingKey: SettingKey,
    val yourValue: String,
    val acceptedValue: String,
    val conflictedAt: Instant,
)
