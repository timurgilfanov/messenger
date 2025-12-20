package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.ObserveSettingsRepositoryError

/**
 * Errors that can occur during settings observation operations.
 *
 * Represents failures at the use case layer, combining identity retrieval errors
 * with repository-level settings observation errors.
 */
sealed interface ObserveSettingsError {
    /**
     * Failed to retrieve current user identity.
     *
     * This error occurs when [IdentityRepository] cannot provide the identity needed
     * to observe the settings.
     */
    data object Unauthorized : ObserveSettingsError

    /**
     * Settings observation operation failed at the repository layer.
     *
     * Wraps errors from [ObserveSettingsRepositoryError] such as insufficient storage,
     * data corruption, or settings reset to defaults.
     */
    data class ObserveSettingsRepository(val error: ObserveSettingsRepositoryError) :
        ObserveSettingsError
}
