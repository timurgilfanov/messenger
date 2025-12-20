package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

class ObserveSettingsUseCaseImpl(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) : ObserveSettingsUseCase {
    companion object {
        private const val TAG = "ObserveSettingsUseCase"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>> =
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
