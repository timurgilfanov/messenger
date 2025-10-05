package timur.gilfanov.messenger.data.source.remote

sealed interface RemovePictureRemoteDataSourceError : RemoteUserDataSourceError {
    data object PictureNotFound : RemovePictureRemoteDataSourceError
}
