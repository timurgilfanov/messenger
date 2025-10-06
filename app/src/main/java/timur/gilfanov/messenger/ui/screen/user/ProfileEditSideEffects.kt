package timur.gilfanov.messenger.ui.screen.user

import timur.gilfanov.messenger.domain.usecase.user.RemovePictureError

sealed interface ProfileEditSideEffects {
    data object PictureUpdated : ProfileEditSideEffects
    data object PictureRemoved : ProfileEditSideEffects
    data class PictureRemovingFailed(val reason: RemovePictureError) : ProfileEditSideEffects
}
