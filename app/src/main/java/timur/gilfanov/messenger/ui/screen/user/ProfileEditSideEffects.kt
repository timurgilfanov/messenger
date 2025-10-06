package timur.gilfanov.messenger.ui.screen.user

sealed interface ProfileEditSideEffects {
    data object PictureUpdated : ProfileEditSideEffects
    data object PictureRemoved : ProfileEditSideEffects
}
