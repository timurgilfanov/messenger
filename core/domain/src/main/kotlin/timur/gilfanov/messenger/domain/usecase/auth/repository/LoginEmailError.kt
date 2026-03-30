package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Email errors specific to the login context.
 *
 * [EmailValidationError] (format errors) and [EmailUnknownError] also implement this interface,
 * so any `LoginEmailError` may be a format error, a login-specific state error, or an unknown error.
 *
 * - [EmailNotExists] — no account found for the given email address
 */
sealed interface LoginEmailError {
    data object EmailNotExists : LoginEmailError
}
