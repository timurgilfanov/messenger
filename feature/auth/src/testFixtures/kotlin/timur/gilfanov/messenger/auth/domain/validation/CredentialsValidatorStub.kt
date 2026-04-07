package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

class CredentialsValidatorStub(var error: CredentialsValidationError? = null) :
    CredentialsValidator {

    override fun validate(
        credentials: Credentials,
    ): ResultWithError<Unit, CredentialsValidationError> {
        val localError = error
        return if (localError == null) {
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(localError)
        }
    }

    override fun validate(email: Email): ResultWithError<Unit, EmailValidationError> =
        when (val e = error) {
            is CredentialsValidationError.Email -> ResultWithError.Failure(e.reason)
            else -> ResultWithError.Success(Unit)
        }

    override fun validate(password: Password): ResultWithError<Unit, PasswordValidationError> =
        when (val e = error) {
            is CredentialsValidationError.Password -> ResultWithError.Failure(e.reason)
            else -> ResultWithError.Success(Unit)
        }
}
