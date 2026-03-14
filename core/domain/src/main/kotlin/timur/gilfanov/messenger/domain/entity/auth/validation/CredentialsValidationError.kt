package timur.gilfanov.messenger.domain.entity.auth.validation

import timur.gilfanov.messenger.domain.entity.ValidationError

/**
 * Validation errors for [timur.gilfanov.messenger.domain.entity.auth.Credentials].
 */
sealed class CredentialsValidationError : ValidationError {
    data object BlankEmail : CredentialsValidationError()
    data object InvalidEmailFormat : CredentialsValidationError()
    data object NoAtInEmail : CredentialsValidationError()
    data class EmailTooLong(val maxLength: Int) : CredentialsValidationError()
    data object NoDomainAtEmail : CredentialsValidationError()
    data class ForbiddenCharacterInEmail(val character: Char) : CredentialsValidationError()

    data class PasswordTooShort(val minLength: Int) : CredentialsValidationError()
    data class PasswordTooLong(val maxLength: Int) : CredentialsValidationError()
    data class ForbiddenCharacterInPassword(val character: Char) : CredentialsValidationError()
    data class PasswordMustContainNumbers(val minNumbers: Int) : CredentialsValidationError()
    data class PasswordMustContainAlphabet(val minAlphabet: Int) : CredentialsValidationError()
}
