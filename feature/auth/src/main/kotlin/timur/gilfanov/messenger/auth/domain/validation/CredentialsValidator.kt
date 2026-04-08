package timur.gilfanov.messenger.auth.domain.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

interface CredentialsValidator {
    fun validate(credentials: Credentials): ResultWithError<Unit, CredentialsValidationError>
    fun validate(email: Email): ResultWithError<Unit, EmailValidationError>
    fun validate(password: Password): ResultWithError<Unit, PasswordValidationError>
}
