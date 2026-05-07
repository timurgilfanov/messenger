package timur.gilfanov.messenger.profile.data.source.remote

/**
 * Errors specific to picture removal remote operations.
 *
 * Defines operation-specific errors returned by the backend when removing pictures.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Common Errors
 * - [RemoteProfileDataSource] - Wraps remote profile data source errors
 */
sealed interface RemovePictureRemoteDataSourceError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRemoteDataSourceError

    /**
     * Common remote profile data source errors.
     *
     * @property error The underlying remote profile data source error
     */
    data class RemoteProfileDataSource(val error: RemoteProfileDataSourceError) :
        RemovePictureRemoteDataSourceError
}
