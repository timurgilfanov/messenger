package timur.gilfanov.messenger.auth.login

sealed interface LoginSideEffects {
    data object NavigateToChatList : LoginSideEffects
    data object NavigateToSignup : LoginSideEffects
}
