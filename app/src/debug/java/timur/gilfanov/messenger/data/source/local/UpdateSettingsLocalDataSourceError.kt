package timur.gilfanov.messenger.data.source.local

/**
 * Errors that can occur during settings update operations.
 *
 * Represents failures when updating user settings using a transformation function.
 */
sealed interface UpdateSettingsLocalDataSourceError {
    /**
     * The transformation function threw an exception.
     *
     * Occurs when the provided transformation function fails to execute successfully.
     *
     * @property exception The exception thrown by the transformation function
     */
    data class TransformError(val exception: Exception) : UpdateSettingsLocalDataSourceError

    /**
     * An error occurred in the underlying local user data source.
     *
     * Wraps errors from reading or writing settings data.
     *
     * @property error The underlying local user data source error
     */
    data class LocalUserDataSource(val error: LocalUserDataSourceError) :
        UpdateSettingsLocalDataSourceError
}
