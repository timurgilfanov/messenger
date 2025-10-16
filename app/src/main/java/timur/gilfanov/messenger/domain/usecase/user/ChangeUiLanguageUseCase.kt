package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.mapError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository

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
 * - [ChangeUiLanguageError.ChangeLanguageRepository]: Language change operation failed
 */
@Suppress("KDocUnresolvedReference")
class ChangeUiLanguageUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        newUiLanguage: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageError> =
        identityRepository.identity.first().foldWithErrorMapping(
            onSuccess = { identity ->
                settingsRepository.changeUiLanguage(identity, newUiLanguage)
                    .mapError { error ->
                        ChangeUiLanguageError.ChangeLanguageRepository(error)
                    }
            },
            onFailure = {
                ChangeUiLanguageError.Unauthorized
            },
        )
}
