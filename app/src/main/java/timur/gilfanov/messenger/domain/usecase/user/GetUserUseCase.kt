package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

class GetUserUseCase {
    operator fun invoke(userId: UserId): ResultWithError<Profile, GetUserError> {
        TODO("Not implemented yet")
    }
}
