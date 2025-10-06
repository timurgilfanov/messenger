package timur.gilfanov.messenger.ui.screen.user

import timur.gilfanov.messenger.domain.entity.user.UiLanguage

/**
 * UI model for a language option in the language selection screen.
 *
 * Represents a selectable language with its display name and domain value.
 *
 * @property name Localized display name of the language (e.g., "English", "Deutsch")
 * @property value Domain entity representing the language
 */
data class LanguageItem(val name: String, val value: UiLanguage)
