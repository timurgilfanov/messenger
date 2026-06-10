package timur.gilfanov.messenger.ui.activity

sealed interface MainActivitySideEffect {
    sealed interface Navigation : MainActivitySideEffect {
        data object Unauthenticated : Navigation
        data object Authenticated : Navigation
    }
}
