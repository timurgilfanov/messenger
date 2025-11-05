package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors that can occur during language change repository operations.
 *
 * Represents failures when changing user language preferences at the repository layer.
 */
sealed interface ChangeLanguageRepositoryError {

    // TODO Add more specific errors
    data object Transient : ChangeLanguageRepositoryError
    data object InsufficientStorage : ChangeLanguageRepositoryError
}
