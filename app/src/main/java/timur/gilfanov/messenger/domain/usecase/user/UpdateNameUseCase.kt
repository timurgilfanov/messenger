package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UserId

class UpdateNameUseCase {
    operator fun invoke(userId: UserId, newName: String): ResultWithError<Unit, UpdateNameError> {
        TODO("Not implemented yet")
    }
}
