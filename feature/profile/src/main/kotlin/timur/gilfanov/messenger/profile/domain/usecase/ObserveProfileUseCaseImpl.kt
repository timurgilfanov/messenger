package timur.gilfanov.messenger.profile.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository

class ObserveProfileUseCaseImpl(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ObserveProfileUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ResultWithError<Profile, ObserveProfileError>> =
        authRepository.authState
            .map { state ->
                when (state) {
                    is AuthState.Authenticated -> state.session.toUserScopeKey()
                    AuthState.Unauthenticated -> null
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { userScopeKey ->
                if (userScopeKey != null) {
                    profileRepository.observeCurrentProfile(userScopeKey)
                        .map { result ->
                            result.bimap(
                                onSuccess = { it },
                                onFailure = ObserveProfileRepositoryError::toObserveProfileError,
                            )
                        }
                } else {
                    flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized))
                }
            }
}

private fun ObserveProfileRepositoryError.toObserveProfileError(): ObserveProfileError =
    when (this) {
        is ObserveProfileRepositoryError.LocalOperationFailed ->
            ObserveProfileError.LocalOperationFailed(error)

        is ObserveProfileRepositoryError.RemoteOperationFailed ->
            if (error == RemoteError.Unauthenticated) {
                ObserveProfileError.Unauthorized
            } else {
                ObserveProfileError.RemoteOperationFailed(error)
            }
    }
