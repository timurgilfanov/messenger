package timur.gilfanov.messenger.profile.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase

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
