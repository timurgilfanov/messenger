package timur.gilfanov.messenger.ui.screen.user

sealed interface ProfileEditSideEffects {
    data object PictureUpdated
    data class PictureUpdatingFailed(val reason: String)
}
