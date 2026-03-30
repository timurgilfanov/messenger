package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Email errors specific to the signup context.
 *
 * [EmailValidationError] (format errors) and [EmailUnknownError] also implement this interface,
 * so any `SignupEmailError` may be a format error, a signup-specific state error, or an unknown error.
 *
 * - [EmailTaken] — an account with this email address already exists
 */
sealed interface SignupEmailError {
    data object EmailTaken : SignupEmailError
}
