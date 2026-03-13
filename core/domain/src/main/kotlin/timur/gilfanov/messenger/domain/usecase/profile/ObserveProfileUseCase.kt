package timur.gilfanov.messenger.domain.usecase.profile

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile

fun interface ObserveProfileUseCase {
    operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>>
}
