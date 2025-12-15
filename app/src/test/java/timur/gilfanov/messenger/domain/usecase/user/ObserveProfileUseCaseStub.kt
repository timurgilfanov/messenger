package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile

class ObserveProfileUseCaseStub(
    private val flow: Flow<ResultWithError<Profile, ObserveProfileError>>,
) : ObserveProfileUseCase {
    override fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>> = flow
}
