package timur.gilfanov.messenger.ui.screen.settings

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import timur.gilfanov.messenger.domain.usecase.profile.UpdateNameError
import timur.gilfanov.messenger.domain.usecase.profile.UpdatePictureError

/**
 * UI state for profile editing screen.
 *
 * ## Error Handling Strategy
 *
 * This screen demonstrates two patterns for error handling:
 *
 * ### State Errors (shown inline in UI)
 * Errors stored in state are persistent and displayed inline:
 * - [nameInputValidationError] - Real-time validation feedback below input field
 * - [pictureUpdatingError] - Upload failure with retry option and progress indicator
 *
 * **Use state errors when:**
 * - User needs to see error persistently to fix it (validation)
 * - Operation has progress/multiple steps (file upload)
 * - Error should survive configuration changes
 * - User can retry the operation
 *
 * ### Side Effect Errors (shown as toast/snackbar)
 * Errors emitted as side effects are transient one-time notifications:
 * - `PictureRemovingFailed` - Instant operation failure, shown as dismissible toast
 * - `PictureUpdated` - Success confirmation, briefly shown then auto-dismissed
 *
 * **Use side effect errors when:**
 * - Operation is instant/atomic (delete, remove)
 * - Error is informational, doesn't require user action
 * - Should not survive configuration changes
 * - One-time notification is sufficient (toast, snackbar)
 *
 * @see ProfileEditSideEffects for transient error/success notifications
 */
data class ProfileEditUiState(
    val nameInput: TextFieldState,
    val nameInputValidationError: UpdateNameError? = null,
    val picture: Uri?,
    val pictureUpdatingError: UpdatePictureError? = null,
    val pictureUploadState: FileUploadState = FileUploadState.Idle,
)
