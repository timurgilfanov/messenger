package timur.gilfanov.messenger.data.source.local

/**
 * Errors specific to local user data operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - User data not found in local storage
 *
 * ## Common Errors
 * - [LocalDataSource] - Wraps common local data source errors
 */
sealed interface LocalUserDataSourceError {
    /**
     * User data not found in local storage.
     *
     * Indicates the requested user does not exist in the local cache.
     */
    data object UserNotFound : LocalUserDataSourceError

    /**
     * Common local data source errors.
     *
     * @property error The underlying local data source error
     */
    data class LocalDataSource(val error: LocalDataSourceErrorV2) : LocalUserDataSourceError
}
