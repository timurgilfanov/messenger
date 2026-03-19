package timur.gilfanov.messenger.auth.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator

class CredentialsValidatorImpl : CredentialsValidator {

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
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

    private fun validateEmail(email: String): ResultWithError<Unit, CredentialsValidationError> =
        when {
            email.isBlank() -> ResultWithError.Failure(CredentialsValidationError.BlankEmail)
            !email.contains('@') -> ResultWithError.Failure(CredentialsValidationError.NoAtInEmail)
            email.substringAfter('@').isEmpty() ->
                ResultWithError.Failure(CredentialsValidationError.NoDomainAtEmail)
            email.length > MAX_EMAIL_LENGTH ->
                ResultWithError.Failure(CredentialsValidationError.EmailTooLong(MAX_EMAIL_LENGTH))
            !EMAIL_REGEX.matches(email) ->
                ResultWithError.Failure(CredentialsValidationError.InvalidEmailFormat)
            else -> ResultWithError.Success(Unit)
        }

    private fun validatePassword(
        password: String,
    ): ResultWithError<Unit, CredentialsValidationError> = when {
        password.length < MIN_PASSWORD_LENGTH ->
            ResultWithError.Failure(
                CredentialsValidationError.PasswordTooShort(MIN_PASSWORD_LENGTH),
            )
        password.length > MAX_PASSWORD_LENGTH ->
            ResultWithError.Failure(
                CredentialsValidationError.PasswordTooLong(MAX_PASSWORD_LENGTH),
            )
        password.none { it.isDigit() } ->
            ResultWithError.Failure(CredentialsValidationError.PasswordMustContainNumbers(1))
        password.none { it.isLetter() } ->
            ResultWithError.Failure(CredentialsValidationError.PasswordMustContainAlphabet(1))
        else -> ResultWithError.Success(Unit)
    }
}
