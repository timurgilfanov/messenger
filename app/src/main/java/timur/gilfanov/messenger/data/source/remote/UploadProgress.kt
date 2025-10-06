package timur.gilfanov.messenger.data.source.remote

import androidx.annotation.IntRange

/**
 * Upload progress state for file uploads.
 *
 * Represents the current state of a file upload operation, allowing
 * tracking of upload progress for UI feedback.
 */
sealed interface UploadProgress {
    /**
     * Upload is in progress with specified completion percentage.
     *
     * @property percent Upload completion percentage (0-100)
     * @throws IllegalArgumentException if percent is not in range 0-100
     */
    data class Uploading(@param:IntRange(from = 0, to = 100) val percent: Int) : UploadProgress {
        init {
            @Suppress("MagicNumber")
            require(percent in 0..100) { "Progress must be between 0 and 100" }
        }
    }

    /**
     * Upload completed successfully.
     *
     * Indicates the file has been fully uploaded and processed by the backend.
     */
    data object Completed : UploadProgress
}
