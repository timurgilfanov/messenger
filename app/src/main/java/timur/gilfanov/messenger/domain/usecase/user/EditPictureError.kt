package timur.gilfanov.messenger.domain.usecase.user

sealed interface EditPictureError : UserOperationError {
    data class FileSizeOutOfBounds(val size: Int, val min: Int, val max: Int) : EditPictureError
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) : EditPictureError
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) : EditPictureError
    sealed interface PlatformPolicyViolation : EditPictureError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownError(val reason: String) : EditPictureError
}
