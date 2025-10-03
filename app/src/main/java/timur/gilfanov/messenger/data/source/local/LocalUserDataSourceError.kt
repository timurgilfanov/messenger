package timur.gilfanov.messenger.data.source.local

sealed interface LocalUserDataSourceError : LocalDataSourceErrorV2 {
    data object UserNotFound : LocalUserDataSourceError
}
