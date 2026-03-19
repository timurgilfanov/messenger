package timur.gilfanov.messenger.auth.login

sealed interface LoginSideEffects {
    data object NavigateToChatList : LoginSideEffects
    data object NavigateToSignup : LoginSideEffects
    data object OpenAppSettings : LoginSideEffects
    data object OpenStorageSettings : LoginSideEffects
    data class ShowSnackbar(val message: LoginSnackbarMessage) : LoginSideEffects
}
