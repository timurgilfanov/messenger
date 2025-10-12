package timur.gilfanov.messenger.data.source.local

/**
 * Errors that can occur during settings update operations.
 *
 * These errors are specific to the [LocalSettingsDataSource.updateSettings] operation,
 * which atomically reads, transforms, and writes settings.
 */
sealed interface UpdateSettingsLocalDataSourceError {
    /**
     * Settings not found in local storage.
     *
     * Indicates that settings don't exist when attempting to update them. This occurs when:
     * - Settings were never created for the user
     * - Settings metadata indicates EMPTY state (lastModifiedAt == 0)
     *
     * Update operations require existing settings to transform. If settings don't exist,
     * the repository layer should trigger recovery to initialize settings first.
     */
    data object SettingsNotFound : UpdateSettingsLocalDataSourceError

    /**
     * The transformation function threw an exception.
     *
     * Occurs when the provided transformation function fails to execute successfully.
     * This indicates a logic error in the transformation code, not a storage error.
     *
     * @property exception The exception thrown by the transformation function
     */
    data class TransformError(val exception: Exception) : UpdateSettingsLocalDataSourceError

    /**
     * Low-level data source error.
     *
     * Wraps underlying storage errors such as I/O failures during read or write operations,
     * or serialization/deserialization errors.
     *
     * @property error The underlying data source error
     */
    data class LocalDataSource(val error: LocalDataSourceErrorV2) :
        UpdateSettingsLocalDataSourceError
}
