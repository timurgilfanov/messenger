package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to remote user data operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - User does not exist on the backend
 *
 * ## Common Errors
 * - [RemoteDataSource] - Wraps common remote data source errors
 */
sealed interface RemoteUserDataSourceError {
    /**
     * User does not exist on the backend.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : RemoteUserDataSourceError

    /**
     * Common remote data source errors.
     *
     * @property error The underlying remote data source error
     */
    data class RemoteDataSource(val error: RemoteDataSourceErrorV2) : RemoteUserDataSourceError
}
