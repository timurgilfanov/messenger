package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError

fun interface LogoutUseCase {
    suspend operator fun invoke(): ResultWithError<Unit, LogoutError>
}
