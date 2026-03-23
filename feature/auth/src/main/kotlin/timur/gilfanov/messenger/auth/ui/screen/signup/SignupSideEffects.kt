package timur.gilfanov.messenger.auth.ui.screen.signup

sealed interface SignupSideEffects {
    data object NavigateToChatList : SignupSideEffects
    data object OpenAppSettings : SignupSideEffects
    data object OpenStorageSettings : SignupSideEffects
    data class ShowSnackbar(val message: SignupSnackbarMessage) : SignupSideEffects
}
