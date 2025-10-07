package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to language change remote operations.
 *
 * Defines operation-specific errors returned by the backend when changing
 * language preferences.
 *
 * ## Operation-Specific Errors
 * - [LanguageNotChangedForAllDevices] - Partial synchronization failure
 *
 * ## Common Errors
 * - [RemoteUserDataSource] - Wraps remote user data source errors
 */
sealed interface ChangeUiLanguageRemoteDataSourceError {
    /**
     * Language preference was updated on some devices but not all.
     *
     * Indicates partial success where synchronization failed for some devices.
     */
    data object LanguageNotChangedForAllDevices : ChangeUiLanguageRemoteDataSourceError

    /**
     * Common remote user data source errors.
     *
     * @property error The underlying remote user data source error
     */
    data class RemoteUserDataSource(val error: RemoteUserDataSourceError) :
        ChangeUiLanguageRemoteDataSourceError
}
