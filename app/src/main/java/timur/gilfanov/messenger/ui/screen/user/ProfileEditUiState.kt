package timur.gilfanov.messenger.ui.screen.user

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError

data class ProfileEditUiState(
    val nameInput: TextFieldState,
    val nameInputValidationError: TextValidationError? = null,
    val picture: Uri?,
    val pictureValidationError: ProfilePictureValidationError? = null,
    val pictureUploadProgress: Int? = null,
    val pictureUploadError: ProfilePictureUploadError? = null,
)
