package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Use case for observing the current user's profile.
 *
 * Emits profile data as a continuous flow, allowing the UI to reactively
 * update when profile information changes.
 *
 * Note: Currently returns a hardcoded profile for development purposes.
 */
class ObserveProfileUseCase {

    /**
     * Observes the current user's profile.
     *
     * @return A [kotlinx.coroutines.flow.Flow] emitting [timur.gilfanov.messenger.domain.entity.ResultWithError] containing either the user's [timur.gilfanov.messenger.domain.entity.user.Profile]
     *         or an [ObserveProfileError] if observation fails.
     */
    operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>> = flowOf(
        ResultWithError.Success(
            Profile(
                UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                "Timur",
                null,
            ),
        ),
    )
}
