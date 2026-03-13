package timur.gilfanov.messenger.domain.usecase.profile

/**
 * Errors that can occur during profile observation operations.
 *
 * Represents failures retrieving profile-related identity or repository data for profile access.
 */
sealed interface ObserveProfileError {
    /**
     * Failed to retrieve current user identity for profile observation.
     *
     * This error occurs when the identity needed for profile observation cannot be retrieved.
     */
    data object Unauthorized : ObserveProfileError
}
