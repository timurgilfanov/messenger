package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

interface CredentialsValidator {
    fun validate(credentials: Credentials): ResultWithError<Unit, CredentialsValidationError>
}
