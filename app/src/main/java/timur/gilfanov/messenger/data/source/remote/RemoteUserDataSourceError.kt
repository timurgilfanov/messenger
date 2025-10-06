package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to remote user data operations.
 *
 * Extends [RemoteDataSourceErrorV2] with user-specific error cases.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - User does not exist on the backend
 *
 * ## Inherited Errors
 * Inherits all errors from [RemoteDataSourceErrorV2] such as network issues,
 * authentication failures, and rate limiting.
 */
sealed interface RemoteUserDataSourceError : RemoteDataSourceErrorV2 {
    /**
     * User does not exist on the backend.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : RemoteUserDataSourceError
}
