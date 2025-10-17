package timur.gilfanov.messenger.domain.usecase.user.repository

import timur.gilfanov.messenger.domain.entity.user.Settings

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

    /**
     * Settings conflict detected between local and remote versions.
     *
     * Both local and remote settings have been modified since last sync.
     *
     * @property localSettings The current local settings with user modifications
     * @property remoteSettings The remote settings from backup
     */
    data class SettingsConflict(
        val localSettings: Settings,
        val remoteSettings: Settings,
    ) : GetSettingsRepositoryError
}
