package timur.gilfanov.messenger.ui.screen.user

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import timur.gilfanov.messenger.domain.usecase.user.RemovePictureError
import timur.gilfanov.messenger.domain.usecase.user.UpdateNameError
import timur.gilfanov.messenger.domain.usecase.user.UpdatePictureError

data class ProfileEditUiState(
    val nameInput: TextFieldState,
    val nameInputValidationError: UpdateNameError? = null,
    val picture: Uri?,
    val pictureUpdatingError: UpdatePictureError? = null,
    val pictureUpdatingProgress: Int? = null,
    val pictureRemovingError: RemovePictureError? = null,
)
