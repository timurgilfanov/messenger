package timur.gilfanov.messenger.ui.screen.settings

import androidx.annotation.IntRange

/**
 * Upload state for file operations.
 *
 * Represents the current state of any file upload operation (pictures, documents,
 * videos, etc.) with type-safe progress tracking. This sealed interface ensures
 * exhaustive handling of upload states and prevents invalid state combinations.
 *
 * ## Usage Example
 * ```kotlin
 * when (uploadState) {
 *     FileUploadState.Idle -> hideProgressIndicator()
 *     is FileUploadState.Uploading -> showProgress(uploadState.percent)
 * }
 * ```
 *
 * @see ProfileEditUiState.pictureUploadState for usage in profile picture uploads
 */
sealed interface FileUploadState {
    /**
     * No upload in progress.
     *
     * The initial and final state when no file is being uploaded.
     */
    data object Idle : FileUploadState

    /**
     * Upload in progress with completion percentage.
     *
     * @property percent Upload completion percentage (0-100)
     * @throws IllegalArgumentException if percent is not in range 0-100
     */
    data class Uploading(@field:IntRange(from = 0, to = 100) val percent: Int) : FileUploadState {
        init {
            @Suppress("MagicNumber")
            require(percent in 0..100) { "Progress must be between 0 and 100, got $percent" }
        }
    }
}
