package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.usecase.common.ErrorReason

/**
 * Common errors for local data source operations.
 *
 * Focused on infrastructure-level failures during I/O and serialization.
 * Entity-specific errors (e.g., UserDataNotFound) are defined in separate
 * interfaces (e.g., LocalProfileDataSourceError) and compose this interface
 * for common infrastructure errors.
 *
 * ## Migration from LocalDataSourceError
 *
 * This interface replaces [LocalDataSourceError] with better separation of concerns:
 *
 * ### Errors moved to entity-specific interfaces:
 * - ChatNotFound, MessageNotFound, ParticipantNotFound → LocalChatDataSourceError, etc.
 *
 * ### Errors moved to operation-specific interfaces:
 * - DuplicateEntity, RelatedEntityMissing → Operation-specific error types
 *
 * ### Errors mapped to infrastructure errors:
 * - StorageUnavailable → ReadError or WriteError with IOException
 *
 */
sealed interface LocalDataSourceErrorV2 {
    /**
     * Storage read operation failed.
     *
     * Occurs when reading data from local storage (DataStore, Room, etc.) fails.
     * Common causes include I/O errors, missing files, or corrupted data.
     *
     * @property reason Description for logging of the cause of read failure
     */
    data class ReadError(val reason: ErrorReason) :
        LocalDataSourceErrorV2,
        LocalDataSourceReadError

    /**
     * Storage write operation failed.
     *
     * Occurs when writing data to local storage (DataStore, Room, etc.) fails.
     * Common causes include I/O errors, insufficient storage space, or permission issues.
     *
     * @property reason Description for logging of the cause of write failure
     */
    data class WriteError(val reason: ErrorReason) :
        LocalDataSourceErrorV2,
        LocalDataSourceWriteError

    /**
     * Data serialization failed.
     *
     * Occurs when converting domain entities to storage format fails.
     * This typically indicates a programming error in serialization logic.
     *
     * @property reason Description for logging of the cause of serialization failure
     */
    data class SerializationError(val reason: ErrorReason) :
        LocalDataSourceErrorV2,
        LocalDataSourceWriteError

    /**
     * Data deserialization failed.
     *
     * Occurs when converting storage format to domain entities fails.
     * Common causes include schema migration issues or corrupted data.
     *
     * @property reason Description for logging of the cause of deserialization failure
     */
    data class DeserializationError(val reason: ErrorReason) :
        LocalDataSourceErrorV2,
        LocalDataSourceReadError
}

/**
 * Marker interface for write-path failures that may allow clients to distinguish
 * between transient I/O problems and permanent serialization/validation bugs.
 */
sealed interface LocalDataSourceWriteError

/**
 * Marker interface for read-path failures to help clients decide whether retrying
 * the operation or falling back to a recovery flow makes sense.
 */
sealed interface LocalDataSourceReadError
