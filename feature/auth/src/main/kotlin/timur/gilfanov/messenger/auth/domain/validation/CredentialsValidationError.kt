package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

/**
 * Validation errors for [timur.gilfanov.messenger.domain.entity.auth.Credentials].
 */
sealed interface CredentialsValidationError : ValidationError {
    data class Email(val reason: EmailValidationError) : CredentialsValidationError
    data class Password(val reason: PasswordValidationError) : CredentialsValidationError
}
