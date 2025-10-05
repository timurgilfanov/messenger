package timur.gilfanov.messenger.data.source.remote

sealed interface ChangeUiLanguageRemoteDataSourceError : RemoteDataSourceErrorV2 {
    data object LanguageNotChangedForAllDevices : ChangeUiLanguageRemoteDataSourceError
}
