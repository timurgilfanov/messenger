package timur.gilfanov.messenger.ui.screen.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveSettingsRepositoryError

/**
 * Side effects for the user profile screen.
 *
 * Represents one-time events that should trigger navigation or show messages,
 * rather than persistent UI state changes.
 */
sealed interface UserSideEffects {

    /**
     * User is not authenticated.
     *
     * Should trigger navigation to authentication flow.
     */
    data object Unauthorized : UserSideEffects

    /**
     * Failed to retrieve user profile.
     *
     * @property error The error that occurred during profile observation.
     */
    data class GetProfileFailed(val error: ObserveProfileRepositoryError) : UserSideEffects

    /**
     * Failed to retrieve user settings.
     *
     * @property error The error that occurred during settings observation.
     */
    data class GetSettingsFailed(val error: ObserveSettingsRepositoryError) : UserSideEffects
}
