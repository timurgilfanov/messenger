package timur.gilfanov.messenger.data.source.remote

sealed interface RemoteProfileDataSourceError : RemoteUserDataSourceError {
    data class NameValidationFailed(val reason: String) : RemoteProfileDataSourceError
    data class PictureUploadFailed(val reason: String) : RemoteProfileDataSourceError
    data object PictureTooLarge : RemoteProfileDataSourceError
}
