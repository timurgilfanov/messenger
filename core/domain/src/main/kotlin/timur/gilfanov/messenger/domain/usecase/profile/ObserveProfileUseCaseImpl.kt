package timur.gilfanov.messenger.domain.usecase.profile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository

class ObserveProfileUseCaseImpl(private val authRepository: AuthRepository) :
    ObserveProfileUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>> =
        authRepository.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.Authenticated ->
                    flowOf(
                        ResultWithError.Success(
                            Profile(
                                id = state.session.userId,
                                name = "Timur",
                                pictureUrl = null,
                            ),
                        ),
                    )

                AuthState.Unauthenticated ->
                    flowOf(
                        ResultWithError.Failure(ObserveProfileError.Unauthorized),
                    )
            }
        }
}
