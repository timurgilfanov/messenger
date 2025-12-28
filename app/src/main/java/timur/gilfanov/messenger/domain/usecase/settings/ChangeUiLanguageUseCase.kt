package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.mapError
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Changes user's UI language preference.
 *
 * Updates the user's preferred language for the application interface. The operation
 * requires the current user's identity which is obtained from [IdentityRepository].
 *
 * @param newUiLanguage The new language preference to set
 * @return Success or failure with [ChangeUiLanguageError]
 *
 * ## Error Handling
 * - [ChangeUiLanguageError.Unauthorized]: Current user identity cannot be retrieved
 * - [ChangeUiLanguageError.LocalOperationFailed]: Local storage operation failed
 */
class ChangeUiLanguageUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "ChangeUiLanguageUseCase"
    }

    suspend operator fun invoke(
        newUiLanguage: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageError> =
        identityRepository.identity.first().foldWithErrorMapping(
            onSuccess = { identity ->
                settingsRepository.changeUiLanguage(identity, newUiLanguage)
                    .mapError { error ->
                        logger.e(
                            TAG,
                            "Repository changeUiLanguage failed: $error",
                        )
                        error.toUseCaseError()
                    }
            },
            onFailure = {
                logger.e(TAG, "Unable to resolve identity while changing UI language")
                ChangeUiLanguageError.Unauthorized
            },
        )
}
