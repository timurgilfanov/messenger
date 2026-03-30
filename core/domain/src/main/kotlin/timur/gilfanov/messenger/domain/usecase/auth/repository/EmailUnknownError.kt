package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Catch-all email error for unrecognised server-side rejections in any auth context.
 *
 * Implements both [LoginEmailError] and [SignupEmailError] so it is valid wherever either is
 * expected. Provides backward compatibility when the server introduces new state-related or
 * validation error codes that the client does not yet recognise.
 */
data class EmailUnknownError(val reason: String) :
    LoginEmailError,
    SignupEmailError
