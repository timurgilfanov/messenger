package timur.gilfanov.messenger.domain.entity.auth.validation

import timur.gilfanov.messenger.domain.entity.ValidationError

/**
 * Validation errors for [timur.gilfanov.messenger.domain.entity.auth.Credentials].
 */
sealed class CredentialsValidationError : ValidationError {
    data object BlankEmail : CredentialsValidationError()
    data object InvalidEmailFormat : CredentialsValidationError()
    data class PasswordTooShort(val minLength: Int) : CredentialsValidationError()
}
