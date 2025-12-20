package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.runtime.Immutable
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * UI model for user settings display.
 *
 * Represents user preferences in a UI-friendly format for display in
 * the settings screen.
 *
 * @property language Currently selected UI language
 */
@Immutable
data class SettingsUi(val language: UiLanguage)

fun Settings.toSettingsUi(): SettingsUi = SettingsUi(language = uiLanguage)
