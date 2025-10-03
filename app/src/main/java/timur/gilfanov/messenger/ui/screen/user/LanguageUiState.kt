package timur.gilfanov.messenger.ui.screen.user

import kotlinx.collections.immutable.ImmutableList

data class LanguageUiState(
    val languages: ImmutableList<LanguageItem>,
    val selectedLanguage: LanguageItem,
)
