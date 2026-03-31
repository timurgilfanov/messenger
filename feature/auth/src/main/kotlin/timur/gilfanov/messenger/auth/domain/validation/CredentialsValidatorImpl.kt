package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

class CredentialsValidatorImpl : CredentialsValidator {

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        const val MIN_NUMBERS_IN_PASSWORD = 1
        const val MIN_ALPHABET_IN_PASSWORD = 1
        const val MAX_EMAIL_LENGTH = 254
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
    }

    override fun validate(
        credentials: Credentials,
    ): ResultWithError<Unit, CredentialsValidationError> {
        val emailResult = validateEmail(credentials.email.value)
        if (emailResult is ResultWithError.Failure) return emailResult
        return validatePassword(credentials.password.value)
    }

    private fun validateEmail(
        email: String,
    ): ResultWithError<Unit, CredentialsValidationError.Email> = when {
        email.isBlank() ->
            ResultWithError.Failure(
                CredentialsValidationError.Email(EmailValidationError.BlankEmail),
            )
        !email.contains('@') ->
            ResultWithError.Failure(
                CredentialsValidationError.Email(EmailValidationError.NoAtInEmail),
            )
        email.substringAfter('@').isEmpty() ->
            ResultWithError.Failure(
                CredentialsValidationError.Email(EmailValidationError.NoDomainAtEmail),
            )
        email.length > MAX_EMAIL_LENGTH ->
            ResultWithError.Failure(
                CredentialsValidationError.Email(
                    EmailValidationError.EmailTooLong(MAX_EMAIL_LENGTH),
                ),
            )
        !EMAIL_REGEX.matches(email) ->
            ResultWithError.Failure(
                CredentialsValidationError.Email(EmailValidationError.InvalidEmailFormat),
            )
        else -> ResultWithError.Success(Unit)
    }

    private fun validatePassword(
        password: String,
    ): ResultWithError<Unit, CredentialsValidationError.Password> = when {
        password.length < MIN_PASSWORD_LENGTH ->
            ResultWithError.Failure(
                CredentialsValidationError.Password(
                    PasswordValidationError.PasswordTooShort(MIN_PASSWORD_LENGTH),
                ),
            )
        password.length > MAX_PASSWORD_LENGTH ->
            ResultWithError.Failure(
                CredentialsValidationError.Password(
                    PasswordValidationError.PasswordTooLong(MAX_PASSWORD_LENGTH),
                ),
            )
        password.count { it.isDigit() } < MIN_NUMBERS_IN_PASSWORD ->
            ResultWithError.Failure(
                CredentialsValidationError.Password(
                    PasswordValidationError.PasswordMustContainNumbers(MIN_NUMBERS_IN_PASSWORD),
                ),
            )
        password.count { it.isLetter() } < MIN_ALPHABET_IN_PASSWORD ->
            ResultWithError.Failure(
                CredentialsValidationError.Password(
                    PasswordValidationError.PasswordMustContainAlphabet(MIN_ALPHABET_IN_PASSWORD),
                ),
            )
        else -> ResultWithError.Success(Unit)
    }
}
