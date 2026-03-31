package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Email errors specific to the signup context.
 *
 * [EmailValidationError] (format errors) also implements this interface,
 * so any `SignupEmailError` may be a format error or a signup-specific state error.
 *
 * - [EmailTaken] — an account with this email address already exists
 */
sealed interface SignupEmailError {
    data object EmailTaken : SignupEmailError
}

/**
 * Email errors specific to the login context.
 *
 * [EmailValidationError] (format errors) also implements this interface,
 * so any `LoginEmailError` may be a format error or a login-specific state error.
 *
 * - [EmailNotExists] — no account found for the given email address
 */
sealed interface LoginEmailError {
    data object EmailNotExists : LoginEmailError
}

/**
 * Email format validation errors returned by local validators and unrecognized backend rules.
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

    /**
     * Catch-all for unrecognized server-side email validation rules.
     *
     * Provides backward compatibility when the server introduces new email validation error codes
     * that the client does not yet recognize.
     */
    data class UnknownRuleViolation(val reason: String) : EmailValidationError
}