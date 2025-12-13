package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile

fun interface ObserveProfileUseCase {
    operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>>
}
