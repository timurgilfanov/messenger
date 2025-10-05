package timur.gilfanov.messenger.domain.usecase.user

sealed interface UpdatePictureError : UserOperationError {
    data class FileSizeOutOfBounds(val size: Long, val min: Long, val max: Long) : UpdatePictureError
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) : UpdatePictureError
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) : UpdatePictureError
    sealed interface PlatformPolicyViolation : UpdatePictureError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownError(val reason: String) : UpdatePictureError
}
