package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError

class LogoutUseCaseNoOp : LogoutUseCase {
    override suspend fun invoke(): ResultWithError<Unit, LogoutUseCaseError> =
        ResultWithError.Success(Unit)
}
