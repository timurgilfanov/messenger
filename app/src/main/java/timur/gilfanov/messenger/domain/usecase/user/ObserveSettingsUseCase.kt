package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Use case for observing user settings.
 *
 * Combines identity resolution with settings observation to provide
 * a reactive stream of the authenticated user's settings. Automatically
 * re-fetches settings when user identity changes.
 *
 * @param identityRepository Repository for retrieving the current user identity.
 * @param settingsRepository Repository for observing user settings.
 * @param logger Logger for error diagnostics.
 */
class ObserveSettingsUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "ObserveSettingsUseCase"
    }

    /**
     * Observes the current user's settings.
     *
     * First resolves the user identity, then observes their settings.
     * If identity cannot be resolved, emits [ObserveSettingsError.Unauthorized].
     * If settings observation fails, emits [ObserveSettingsError.ObserveSettingsRepository].
     *
     * @return A [Flow] emitting [ResultWithError] containing either the user's [Settings]
     *         or an [ObserveSettingsError] if observation fails.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>> =
        identityRepository.identity.flatMapLatest { identityResult ->
            identityResult.fold(
                onSuccess = { identity ->
                    settingsRepository.observeSettings(identity)
                        .map { settingsResult ->
                            settingsResult.bimap(
                                onSuccess = { settings ->
                                    settings
                                },
                                onFailure = { error ->
                                    logger.e(TAG, "Settings observation failed: $error")
                                    ObserveSettingsError.ObserveSettingsRepository(error)
                                },
                            )
                        }
                },
                onFailure = {
                    logger.e(TAG, "Unable to resolve identity while observing settings")
                    flowOf(
                        ResultWithError.Failure<Settings, ObserveSettingsError>(
                            ObserveSettingsError.Unauthorized,
                        ),
                    )
                },
            )
        }
}
