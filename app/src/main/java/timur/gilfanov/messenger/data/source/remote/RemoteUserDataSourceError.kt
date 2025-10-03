package timur.gilfanov.messenger.data.source.remote

sealed interface RemoteUserDataSourceError : RemoteDataSourceErrorV2 {
    data object UserNotFound : RemoteUserDataSourceError
}
