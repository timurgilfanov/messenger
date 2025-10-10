package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors that can occur during language change repository operations.
 *
 * Represents failures when changing user language preferences at the repository layer.
 */
sealed interface ChangeLanguageRepositoryError {
    /**
     * The language preference was not changed.
     *
     * @property transient Indicates if the error is transient (retriable) or permanent
     */
    data class LanguageNotChanged(val transient: Boolean) : ChangeLanguageRepositoryError

    /**
     * Settings were not found and were reset to default values.
     *
     * The language change operation triggered a settings reset because no settings
     * existed for the user.
     */
    data object SettingsResetToDefaults : ChangeLanguageRepositoryError

    /**
     * Settings could not be created or initialized.
     *
     * Occurs when settings are missing and cannot be restored from any source.
     */
    data object SettingsEmpty : ChangeLanguageRepositoryError

    /**
     * Language change succeeded locally but backup to remote failed.
     *
     * The language preference was updated locally but could not be synchronized
     * to the remote server.
     *
     * @property error Details about the backup failure
     */
    data class Backup(val error: SettingsChangeBackupError) : ChangeLanguageRepositoryError
}
