package timur.gilfanov.messenger.data.source.local

sealed interface LocalDataSourceError {
    data object DatabaseUnavailable : LocalDataSourceError
    data object StorageFailure : LocalDataSourceError
    data object ChatNotFound : LocalDataSourceError
    data object MessageNotFound : LocalDataSourceError
    data object InvalidData : LocalDataSourceError
    data class UnknownError(val cause: Throwable) : LocalDataSourceError
}
