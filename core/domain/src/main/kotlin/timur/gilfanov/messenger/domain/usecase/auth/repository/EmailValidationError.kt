package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Email format validation errors returned by local validators.
 *
 * Implements both [LoginEmailError] and [SignupEmailError] so instances flow into both login
 * and signup use cases without any mapping. These errors describe structural problems with the
 * email string itself, independent of server-side account state.
 */
sealed interface EmailValidationError :
    LoginEmailError,
    SignupEmailError {
    data object BlankEmail : EmailValidationError
    data object InvalidEmailFormat : EmailValidationError
    data object NoAtInEmail : EmailValidationError
    data class EmailTooLong(val maxLength: Int) : EmailValidationError
    data object NoDomainAtEmail : EmailValidationError
}
