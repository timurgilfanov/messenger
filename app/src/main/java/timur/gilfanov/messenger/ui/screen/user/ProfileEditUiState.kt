package timur.gilfanov.messenger.ui.screen.user

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.user.UpdatePictureError

data class ProfileEditUiState(
    val nameInput: TextFieldState,
    val nameInputValidationError: TextValidationError? = null,
    val picture: Uri?,
    val pictureUpdatingError: UpdatePictureError? = null,
    val pictureUpdatingProgress: Int? = null,
)
