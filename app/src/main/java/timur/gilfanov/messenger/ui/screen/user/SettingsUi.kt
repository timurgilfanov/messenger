package timur.gilfanov.messenger.ui.screen.user

import timur.gilfanov.messenger.domain.entity.user.UiLanguage

/**
 * UI model for user settings display.
 *
 * Represents user preferences in a UI-friendly format for display in
 * the settings screen.
 *
 * @property language Display name of the currently selected language
 */
data class SettingsUi(val language: UiLanguage)
