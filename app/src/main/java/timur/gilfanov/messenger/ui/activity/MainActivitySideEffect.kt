package timur.gilfanov.messenger.ui.activity

sealed interface MainActivitySideEffect {
    data object NavigateToLogin : MainActivitySideEffect
}
