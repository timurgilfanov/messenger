package timur.gilfanov.messenger.ui.activity

sealed interface MainActivitySideEffects {
    sealed interface Navigation : MainActivitySideEffects {
        data object Unauthenticated : Navigation
        data object Authenticated : Navigation
    }
}
