package timur.gilfanov.messenger.data.source.local

/**
 * Errors that can occur when observing settings.
 *
 * These errors are specific to the [LocalSettingsDataSource.observe] operation.
 */
sealed interface GetSettingsLocalDataSourceError {

    data object NoSettings : GetSettingsLocalDataSourceError

    // todo extract Recoverable and UnknownError to common local data source error?

    /**
     * Recoverable errors that can be resolved by user action.
     *
     * These errors indicate specific issues that the user can address
     * to restore settings functionality.
     */
    sealed interface Recoverable : GetSettingsLocalDataSourceError { // todo rename to StorageError?
        /**
         * Insufficient storage space available on the device.
         *
         * The system cannot save or update settings due to lack of storage.
         * User should free up storage space and try again.
         */
        data object InsufficientStorage : Recoverable

        /**
         * Settings data is corrupted and cannot be read.
         *
         * The stored settings have become corrupted or unreadable.
         * User should clear app data to rebuild settings from defaults or remote.
         */
        data object DataCorruption : Recoverable

        /**
         * Access to settings storage is denied.
         *
         * The app does not have necessary permissions to access settings storage.
         * User should verify app permissions or reinstall the app.
         */
        data object AccessDenied : Recoverable

        /**
         * Settings storage is read-only and cannot be modified.
         *
         * The system cannot save setting changes due to storage restrictions.
         * User should check storage permissions and available space.
         */
        data object ReadOnly : Recoverable

        /**
         * Settings are temporarily unavailable.
         *
         * A transient condition is preventing access to settings.
         * User should try again in a few moments.
         */
        data object TemporarilyUnavailable : Recoverable
    }

    /**
     * An unexpected error occurred while accessing settings.
     *
     * Represents errors that don't fit known failure categories.
     * The cause is always preserved for diagnostics and logging, but should not
     * be used for control flow decisions. Use error type hierarchy for
     * decision making instead.
     *
     * @param cause The underlying exception that caused this error.
     */
    data class UnknownError(val cause: Throwable) : GetSettingsLocalDataSourceError
}
