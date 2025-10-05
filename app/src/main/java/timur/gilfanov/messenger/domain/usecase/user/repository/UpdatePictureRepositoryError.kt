package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface UpdatePictureRepositoryError : UserRepositoryError {
    data class FileSizeOutOfBounds(val size: Int, val min: Int, val max: Int) :
        UpdatePictureRepositoryError

    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) :
        UpdatePictureRepositoryError

    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) :
        UpdatePictureRepositoryError

    sealed interface PlatformPolicyViolation : UpdatePictureRepositoryError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownError(val reason: String) : UpdatePictureRepositoryError
}
