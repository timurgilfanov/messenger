package timur.gilfanov.messenger.domain.usecase.user

sealed interface ChangeUiLanguageError : UserOperationError {
    data object LanguageNotChangedForAllDevices : ChangeUiLanguageError
}
