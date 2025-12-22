package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.profile.RemovePictureError

/**
 * One-time side effects for profile editing screen.
 *
 * These events are transient and should not survive configuration changes.
 * Typically shown as toast/snackbar notifications.
 *
 * ## When to Use Side Effects vs State Errors
 *
 * **Use side effects for:**
 * - Instant/atomic operations (remove picture - no progress, immediate result)
 * - Success confirmations (picture updated/removed - brief notification)
 * - Errors that don't require user action (informational only)
 *
 * **Use state errors for:**
 * - Operations with progress indicators (picture upload - see [ProfileEditUiState.pictureUpdatingError])
 * - Validation errors requiring user correction (name input - see [ProfileEditUiState.nameInputValidationError])
 * - Errors that should persist and be retryable
 *
 * @see ProfileEditUiState for detailed error handling strategy documentation
 */
sealed interface ProfileEditSideEffects {
    data object PictureUpdated : ProfileEditSideEffects
    data object PictureRemoved : ProfileEditSideEffects
    data class PictureRemovingFailed(val reason: RemovePictureError) : ProfileEditSideEffects
}
