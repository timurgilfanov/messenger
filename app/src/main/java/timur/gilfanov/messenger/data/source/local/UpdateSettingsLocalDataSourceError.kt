package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.usecase.common.ErrorReason

/**
 * Errors that can occur during settings update operations.
 *
 * These errors are specific to the [LocalSettingsDataSource.transform] operation,
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
     * @property reason Description for logging of the cause of transformation failure
     */
    data class TransformError(val reason: ErrorReason) : UpdateSettingsLocalDataSourceError

    /**
     * Failures that happened while reading the current settings snapshot before applying the
     * transformation (e.g. I/O or deserialization issues).
     *
     * The [error] is typed with [LocalDataSourceReadError] so callers can distinguish transient
     * read problems from permanent data corruption.
     */
    data class GetSettingsLocalDataSource(val error: LocalDataSourceReadError) :
        UpdateSettingsLocalDataSourceError

    /**
     * Failures that happened while persisting transformed settings (e.g. serialization issues or a
     * write I/O error). The [error] implements [LocalDataSourceWriteError], allowing the caller to
     * infer retryability.
     */
    data class UpdateSettingsLocalDataSource(val error: LocalDataSourceWriteError) :
        UpdateSettingsLocalDataSourceError
}
