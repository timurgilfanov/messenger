package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to picture removal remote operations.
 *
 * Defines operation-specific errors returned by the backend when removing pictures.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Common Errors
 * - [RemoteSettingsDataSource] - Wraps remote settings data source errors
 */
sealed interface RemovePictureRemoteDataSourceError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRemoteDataSourceError

    /**
     * Common remote settings data source errors.
     *
     * @property error The underlying remote settings data source error
     */
    data class RemoteSettingsDataSource(val error: RemoteSettingsDataSourceError) :
        RemovePictureRemoteDataSourceError
}
