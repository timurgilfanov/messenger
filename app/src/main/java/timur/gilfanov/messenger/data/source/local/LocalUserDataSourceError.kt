package timur.gilfanov.messenger.data.source.local

/**
 * Errors specific to local user data operations.
 *
 * Extends [LocalDataSourceErrorV2] with user-specific error cases.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - User data not found in local storage
 *
 * ## Inherited Errors
 * Inherits all errors from [LocalDataSourceErrorV2] such as data corruption
 * and storage access issues.
 */
sealed interface LocalUserDataSourceError : LocalDataSourceErrorV2 {
    /**
     * User data not found in local storage.
     *
     * Indicates the requested user does not exist in the local cache.
     */
    data object UserNotFound : LocalUserDataSourceError
}
