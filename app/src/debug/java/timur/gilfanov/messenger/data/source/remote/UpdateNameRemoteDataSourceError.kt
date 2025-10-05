package timur.gilfanov.messenger.data.source.remote

sealed interface UpdateNameRemoteDataSourceError : RemoteUserDataSourceError {
    data class NotValid(val reason: String) : UpdateNameRemoteDataSourceError
}
