package timur.gilfanov.messenger.domain.usecase.user

/**
 * Normalized outcome for settings synchronization operations.
 *
 * Allows WorkManager workers (and other callers) to convert repository-specific
 * errors into a simplified contract that dictates whether to mark the work as
 * successful, retry it later, or fail permanently.
 */
sealed interface SettingsSyncOutcome {
    /**
     * Synchronization finished successfully.
     */
    data object Success : SettingsSyncOutcome

    /**
     * Synchronization failed due to a transient error and should be retried.
     */
    data object Retry : SettingsSyncOutcome

    /**
     * Synchronization failed permanently and should not be retried.
     */
    data object Failure : SettingsSyncOutcome
}
