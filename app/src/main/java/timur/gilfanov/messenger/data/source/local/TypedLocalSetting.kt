package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.entity.user.Setting
import timur.gilfanov.messenger.domain.entity.user.SettingKey

/**
 * Typed wrapper for individual settings from local storage.
 *
 * Provides type-safe access to settings with sync metadata while hiding
 * Room's [timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity] implementation
 * details from the data source contract.
 *
 * Each sealed subclass wraps a [LocalSetting] of specific domain type (e.g., UiLanguage).
 * This ensures validation happens at the data source boundary when converting from
 * database entities to typed domain values.
 *
 * ## Usage:
 * ```kotlin
 * val setting = localDataSource.getSetting(userId, SettingKey.UI_LANGUAGE)
 * when (setting) {
 *     is TypedLocalSetting.UiLanguage -> {
 *         val language: UiLanguage = setting.setting.value
 *         val version: Int = setting.setting.localVersion
 *     }
 * }
 * ```
 *
 * @property key The setting identifier from domain layer
 */
sealed interface TypedLocalSetting {
    val key: SettingKey
    val setting: LocalSetting<out Setting>

    /**
     * UI language setting with sync metadata.
     *
     * Wraps a [LocalSetting] containing a validated [UiLanguage] domain value.
     *
     * @property setting Typed local setting with UiLanguage value and sync metadata
     */
    data class UiLanguage(
        override val setting: LocalSetting<timur.gilfanov.messenger.domain.entity.user.UiLanguage>,
    ) : TypedLocalSetting {
        override val key: SettingKey = SettingKey.UI_LANGUAGE
    }
}
