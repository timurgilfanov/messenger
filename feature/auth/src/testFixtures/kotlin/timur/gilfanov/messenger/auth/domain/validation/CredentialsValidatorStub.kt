package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

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
}
