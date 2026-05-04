package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Observes the current user's UI language preference.
 *
 * This use case provides a reactive stream of the user's selected UI language,
 * emitting updates whenever the language preference changes. Collection naturally suspends
 * until the auth state has been resolved (see [AuthRepository.authState]).
 *
 * ## Error Handling
 * - [ObserveUiLanguageError.Unauthorized]: Current user is not authenticated
 * - [ObserveUiLanguageError.SettingsUnspecified]: Settings are unspecified
 * - [ObserveUiLanguageError.LocalOperationFailed]: Local storage operation failed
 *
 * @property authRepository Provides access to the current authentication state
 * @property settingsRepository Provides access to user settings including language preference
 */
class ObserveUiLanguageUseCase(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "ObserveUiLanguageUseCase"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ResultWithError<UiLanguage, ObserveUiLanguageError>> =
        authRepository.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.Authenticated ->
                    settingsRepository.observeSettings(state.session.toUserScopeKey())
                        .map { settingsResult ->
                            settingsResult.bimap(
                                onSuccess = { settings ->
                                    settings.uiLanguage
                                },
                                onFailure = { error ->
                                    logger.e(
                                        TAG,
                                        "Settings observation failed: $error",
                                    )
                                    error.toObserveUiLanguageError()
                                },
                            )
                        }

                AuthState.Unauthenticated -> {
                    logger.e(TAG, "Unable to resolve identity while observing UI language")
                    flowOf(
                        ResultWithError.Failure<UiLanguage, ObserveUiLanguageError>(
                            ObserveUiLanguageError.Unauthorized,
                        ),
                    )
                }
            }
        }
}
