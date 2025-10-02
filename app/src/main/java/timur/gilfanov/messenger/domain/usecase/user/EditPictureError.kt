package timur.gilfanov.messenger.domain.usecase.user

sealed interface EditPictureError : UserOperationError {
    data class FileSizeOutOfBounds(val size: Int, val min: Int, val max: Int) : EditPictureError
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) : EditPictureError
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) : EditPictureError
    data class PlatformPolicyViolation(val reason: String) : EditPictureError
}
