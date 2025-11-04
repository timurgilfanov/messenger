package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface GetSettingsRepositoryError {
    /**
     * Settings were not found and were reset to default values.
     */
    data object SettingsResetToDefaults : GetSettingsRepositoryError

    /**
     * Settings could not be created or initialized.
     *
     * Occurs when settings are missing and cannot be restored from any source.
     */
    data object SettingsEmpty : GetSettingsRepositoryError

    /**
     * Recoverable errors that can be resolved by user action.
     */
    sealed interface Recoverable : GetSettingsRepositoryError {
        /**
         * Insufficient storage space available.
         *
         * User can recover by freeing up storage space.
         */
        data object InsufficientStorage : Recoverable

        /**
         * Data corruption detected in local storage.
         *
         * User can recover by clearing app data.
         */
        data object DataCorruption : Recoverable

        /**
         * Settings temporarily unavailable due to transient error.
         *
         * User can recover by trying again later.
         */
        data object TemporarilyUnavailable : Recoverable
    }
}
