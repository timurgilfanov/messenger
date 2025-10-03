package timur.gilfanov.messenger.data.source.local

sealed interface LocalSettingsDataSourceError : LocalUserDataSourceError {
    data object SettingsNotFound : LocalSettingsDataSourceError
}
