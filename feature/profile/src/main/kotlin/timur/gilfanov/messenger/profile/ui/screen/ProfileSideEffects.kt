package timur.gilfanov.messenger.profile.ui.screen

sealed interface ProfileSideEffects {
    sealed interface Feedback : ProfileSideEffects {
        data object ObserveFailed : Feedback
    }
}
