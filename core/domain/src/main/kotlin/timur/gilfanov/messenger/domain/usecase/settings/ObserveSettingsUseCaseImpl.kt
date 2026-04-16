package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

class ObserveSettingsUseCaseImpl(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) : ObserveSettingsUseCase {
    companion object {
        private const val TAG = "ObserveSettingsUseCase"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>> =
        authRepository.authState.flatMapLatest { state ->
            when (state) {
                AuthState.Loading -> emptyFlow()

                is AuthState.Authenticated ->
                    settingsRepository.observeSettings(state.session.toUserScopeKey())
                        .map { settingsResult ->
                            settingsResult.bimap(
                                onSuccess = { settings ->
                                    settings
                                },
                                onFailure = { error ->
                                    logger.e(TAG, "Settings observation failed: $error")
                                    error.toObserveSettingsError()
                                },
                            )
                        }

                AuthState.Unauthenticated -> {
                    logger.e(TAG, "Unable to resolve identity while observing settings")
                    flowOf(
                        ResultWithError.Failure<Settings, ObserveSettingsError>(
                            ObserveSettingsError.Unauthorized,
                        ),
                    )
                }
            }
        }
}
