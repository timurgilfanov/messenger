package timur.gilfanov.messenger.ui.activity

sealed interface MainActivitySideEffect {
    data object Unauthenticated : MainActivitySideEffect
    data object Authenticated : MainActivitySideEffect
}
