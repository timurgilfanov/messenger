package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface ChangeLanguageRepositoryError : SettingsRepositoryError {
    data object LanguageNotChangedForAllDevices : ChangeLanguageRepositoryError
}
