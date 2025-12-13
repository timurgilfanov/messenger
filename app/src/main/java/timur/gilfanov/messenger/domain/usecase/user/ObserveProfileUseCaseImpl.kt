package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

class ObserveProfileUseCaseImpl : ObserveProfileUseCase {

    override operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>> = flowOf(
        ResultWithError.Success(
            Profile(
                UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                "Timur",
                null,
            ),
        ),
    )
}
