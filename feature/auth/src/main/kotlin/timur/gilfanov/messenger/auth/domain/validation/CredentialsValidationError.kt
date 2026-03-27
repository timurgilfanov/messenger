package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ValidationError

/**
 * Validation errors for [timur.gilfanov.messenger.domain.entity.auth.Credentials].
 */
sealed interface CredentialsValidationError : ValidationError {

    sealed interface Email : CredentialsValidationError {
        data object BlankEmail : Email
        data object InvalidEmailFormat : Email
        data object NoAtInEmail : Email
        data class EmailTooLong(val maxLength: Int) : Email
        data object NoDomainAtEmail : Email
    }

    sealed interface Password : CredentialsValidationError {
        data class PasswordTooShort(val minLength: Int) : Password
        data class PasswordTooLong(val maxLength: Int) : Password
        data class PasswordMustContainNumbers(val minNumbers: Int) : Password
        data class PasswordMustContainAlphabet(val minAlphabet: Int) : Password
    }
}
