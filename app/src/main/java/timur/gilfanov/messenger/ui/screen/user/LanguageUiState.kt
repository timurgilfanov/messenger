package timur.gilfanov.messenger.ui.screen.user

import kotlinx.collections.immutable.ImmutableList

/**
 * UI state for the language selection screen.
 *
 * Displays available languages and the currently selected language preference.
 *
 * @property languages List of all available language options
 * @property selectedLanguage The currently selected language
 */
data class LanguageUiState(
    val languages: ImmutableList<LanguageItem>,
    val selectedLanguage: LanguageItem,
)
