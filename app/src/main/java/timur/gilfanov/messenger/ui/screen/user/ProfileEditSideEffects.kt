package timur.gilfanov.messenger.ui.screen.user

sealed interface ProfileEditSideEffects {
    data class LaunchPicturePickerFailed(val reason: String) : ProfileEditSideEffects
    data object PictureUpdated : ProfileEditSideEffects
}
