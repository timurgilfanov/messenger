package timur.gilfanov.messenger.auth.login

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

fun interface LoginWithCredentialsUseCase {
    suspend operator fun invoke(credentials: Credentials): ResultWithError<Unit, LoginUseCaseError>
}
