package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface ChangeLanguageRepositoryError : UserRepositoryError {
    data object LanguageNotChangedForAllDevices : ChangeLanguageRepositoryError
}
