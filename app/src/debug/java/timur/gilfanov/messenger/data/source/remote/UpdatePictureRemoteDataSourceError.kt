package timur.gilfanov.messenger.data.source.remote

sealed interface UpdatePictureRemoteDataSourceError : RemoteUserDataSourceError {
    data class FileSizeOutOfBounds(val size: Int, val min: Int, val max: Int) :
        UpdatePictureRemoteDataSourceError

    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) :
        UpdatePictureRemoteDataSourceError

    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) :
        UpdatePictureRemoteDataSourceError

    sealed interface PlatformPolicyViolation : UpdatePictureRemoteDataSourceError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownError(val reason: String) : UpdatePictureRemoteDataSourceError
}
