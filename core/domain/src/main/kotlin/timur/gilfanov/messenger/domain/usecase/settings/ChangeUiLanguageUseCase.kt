package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.mapError
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Changes user's UI language preference.
 *
 * Updates the user's preferred language for the application interface. The operation
 * requires the current user's authenticated session obtained from [AuthRepository].
 *
 * @param newUiLanguage The new language preference to set
 * @return Success or failure with [ChangeUiLanguageError]
 *
 * ## Error Handling
 * - [ChangeUiLanguageError.Unauthorized]: Current user is not authenticated
 * - [ChangeUiLanguageError.LocalOperationFailed]: Local storage operation failed
 */
class ChangeUiLanguageUseCase(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "ChangeUiLanguageUseCase"
    }

    suspend operator fun invoke(
        newUiLanguage: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageError> =
        when (val state = authRepository.authState.first()) {
            is AuthState.Authenticated ->
                settingsRepository.changeUiLanguage(state.session, newUiLanguage)
                    .mapError { error ->
                        logger.e(
                            TAG,
                            "Repository changeUiLanguage failed: $error",
                        )
                        error.toUseCaseError()
                    }

            AuthState.Unauthenticated -> {
                logger.e(TAG, "Unable to resolve identity while changing UI language")
                ResultWithError.Failure(ChangeUiLanguageError.Unauthorized)
            }
        }
}
