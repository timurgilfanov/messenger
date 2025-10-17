package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors surfaced when applying remote settings into the local cache.
 *
 * The distinction between transient and non-transient errors allows callers to surface
 * appropriate messaging or fallback strategies without needing to inspect lower layers.
 */
sealed interface ApplyRemoteSettingsRepositoryError {
    /**
     * Maps to infrastructure issues (I/O write failures, temporary storage unavailability).
     */
    data object Transient : ApplyRemoteSettingsRepositoryError

    /**
     * Represents permanent failures such as serialization bugs where retrying the same payload
     * would continue to fail until the underlying code changes.
     */
    data object NotTransient : ApplyRemoteSettingsRepositoryError
}
