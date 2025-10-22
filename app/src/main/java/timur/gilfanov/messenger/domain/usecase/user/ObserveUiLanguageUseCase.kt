package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository

/**
 * Observes the current user's UI language preference.
 *
 * This use case provides a reactive stream of the user's selected UI language,
 * emitting updates whenever the language preference changes.
 *
 * ## Error Handling
 * - [ObserveUiLanguageError.Unauthorized]: Current user identity cannot be retrieved
 * - [ObserveUiLanguageError.ObserveLanguageRepository]: Settings retrieval failed
 *
 * @property identityRepository Provides access to the current user's identity
 * @property settingsRepository Provides access to user settings including language preference
 */
class ObserveUiLanguageUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ResultWithError<UiLanguage, ObserveUiLanguageError>> =
        identityRepository.identity.flatMapLatest { identityResult ->
            identityResult.fold(
                onSuccess = { identity ->
                    settingsRepository.observeSettings(identity)
                        .map { settingsResult ->
                            settingsResult.bimap(
                                onSuccess = { settings ->
                                    settings.uiLanguage
                                },
                                onFailure = { error ->
                                    ObserveUiLanguageError.ObserveLanguageRepository(error)
                                },
                            )
                        }
                },
                onFailure = {
                    flowOf(
                        ResultWithError.Failure<UiLanguage, ObserveUiLanguageError>(
                            ObserveUiLanguageError.Unauthorized,
                        ),
                    )
                },
            )
        }
}
