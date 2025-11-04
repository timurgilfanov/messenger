package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface GetSettingsRepositoryError {
    /**
     * Settings were not found and were reset to default values.
     *
     * The language change operation triggered a settings reset because no settings
     * existed for the user.
     */
    data object SettingsResetToDefaults : GetSettingsRepositoryError

    /**
     * Settings could not be created or initialized.
     *
     * Occurs when settings are missing and cannot be restored from any source.
     */
    data object SettingsEmpty : GetSettingsRepositoryError
}
