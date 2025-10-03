package timur.gilfanov.messenger.data.source.remote

sealed interface RemoteSettingsDataSourceError : RemoteUserDataSourceError {
    data object UnsupportedLanguage : RemoteSettingsDataSourceError
}
