package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
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
        val emailResult = validate(credentials.email)
        if (emailResult is ResultWithError.Failure) {
            return ResultWithError.Failure(CredentialsValidationError.Email(emailResult.error))
        }
        return when (val passwordResult = validate(credentials.password)) {
            is ResultWithError.Failure ->
                ResultWithError.Failure(CredentialsValidationError.Password(passwordResult.error))
            is ResultWithError.Success -> ResultWithError.Success(Unit)
        }
    }

    override fun validate(email: Email): ResultWithError<Unit, EmailValidationError> = when {
        email.value.isBlank() ->
            ResultWithError.Failure(EmailValidationError.BlankEmail)
        !email.value.contains('@') ->
            ResultWithError.Failure(EmailValidationError.NoAtInEmail)
        email.value.substringAfter('@').isEmpty() ->
            ResultWithError.Failure(EmailValidationError.NoDomainAtEmail)
        email.value.length > MAX_EMAIL_LENGTH ->
            ResultWithError.Failure(EmailValidationError.EmailTooLong(MAX_EMAIL_LENGTH))
        !EMAIL_REGEX.matches(email.value) ->
            ResultWithError.Failure(EmailValidationError.InvalidEmailFormat)
        else -> ResultWithError.Success(Unit)
    }

    override fun validate(password: Password): ResultWithError<Unit, PasswordValidationError> =
        when {
            password.value.length < MIN_PASSWORD_LENGTH ->
                ResultWithError.Failure(
                    PasswordValidationError.PasswordTooShort(MIN_PASSWORD_LENGTH),
                )
            password.value.length > MAX_PASSWORD_LENGTH ->
                ResultWithError.Failure(
                    PasswordValidationError.PasswordTooLong(MAX_PASSWORD_LENGTH),
                )
            password.value.count { it.isDigit() } < MIN_NUMBERS_IN_PASSWORD ->
                ResultWithError.Failure(
                    PasswordValidationError.PasswordMustContainNumbers(MIN_NUMBERS_IN_PASSWORD),
                )
            password.value.count { it.isLetter() } < MIN_ALPHABET_IN_PASSWORD ->
                ResultWithError.Failure(
                    PasswordValidationError.PasswordMustContainAlphabet(MIN_ALPHABET_IN_PASSWORD),
                )
            else -> ResultWithError.Success(Unit)
        }
}
