package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * UI state for the language selection screen.
 *
 * Displays available languages and the currently selected language preference.
 *
 * @property languages List of all available language options
 * @property selectedLanguage The currently selected language
 */
@Immutable
data class LanguageUiState(
    val languages: ImmutableList<UiLanguage>,
    val selectedLanguage: UiLanguage?,
)
