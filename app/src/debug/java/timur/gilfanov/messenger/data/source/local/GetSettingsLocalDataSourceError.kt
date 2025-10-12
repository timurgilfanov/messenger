package timur.gilfanov.messenger.data.source.local

/**
 * Errors that can occur when observing settings.
 *
 * These errors are specific to the [LocalSettingsDataSource.observeSettings] operation.
 */
sealed interface GetSettingsLocalDataSourceError {
    /**
     * Settings not found in local storage.
     *
     * Indicates that no settings exist for the requested user. This typically occurs when:
     * - User has never had settings created
     * - Settings metadata indicates EMPTY state (lastModifiedAt == 0)
     *
     * This error triggers recovery flow to fetch settings from remote or initialize defaults.
     */
    data object SettingsNotFound : GetSettingsLocalDataSourceError

    /**
     * Low-level data source error.
     *
     * Wraps underlying storage errors such as I/O failures or deserialization errors.
     *
     * @property error The underlying data source error
     */
    data class LocalDataSource(val error: LocalDataSourceErrorV2) : GetSettingsLocalDataSourceError
}
