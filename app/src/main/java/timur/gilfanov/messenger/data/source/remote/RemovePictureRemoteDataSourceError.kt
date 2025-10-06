package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to picture removal remote operations.
 *
 * Defines operation-specific errors returned by the backend when removing pictures.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Inherited Errors
 * Inherits all errors from [RemoteUserDataSourceError] and [RemoteDataSourceErrorV2].
 */
sealed interface RemovePictureRemoteDataSourceError : RemoteUserDataSourceError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRemoteDataSourceError
}
