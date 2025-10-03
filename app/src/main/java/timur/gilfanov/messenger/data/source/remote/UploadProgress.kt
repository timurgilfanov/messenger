package timur.gilfanov.messenger.data.source.remote

sealed interface UploadProgress {
    data class Uploading(val percent: Int) : UploadProgress {
        init {
            @Suppress("MagicNumber")
            require(percent in 0..100) { "Progress must be between 0 and 100" }
        }
    }
    data object Completed : UploadProgress
}
