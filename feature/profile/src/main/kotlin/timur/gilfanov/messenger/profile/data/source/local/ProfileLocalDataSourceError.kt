package timur.gilfanov.messenger.profile.data.source.local

/**
 * Errors specific to local profile data operations.
 *
 * ## User-Specific Errors
 * - [UserDataNotFound] - Profile data not found in local storage
 *
 * ## Common Errors
 * - [LocalDataSource] - Wraps common local data source errors
 */
sealed interface ProfileLocalDataSourceError {
    /**
     * Profile data not found in local storage.
     *
     * Indicates the requested user does not exist in the local cache.
     */
    data object UserDataNotFound : ProfileLocalDataSourceError

    /**
     * Common local data source errors.
     *
     * @property error The underlying local data source error
     */
    data class LocalDataSource(val error: LocalDataSourceErrorV2) : ProfileLocalDataSourceError
}
