package timur.gilfanov.messenger.ui.screen.user

sealed interface LanguageSideEffects {
    data class LanguageNotChangedForAllDevices(val reason: String) : LanguageSideEffects
}
