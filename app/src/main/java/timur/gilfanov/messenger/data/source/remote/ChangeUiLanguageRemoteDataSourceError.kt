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
 * ## Inherited Errors
 * Inherits all errors from [RemoteDataSourceErrorV2] such as network issues
 * and authentication failures.
 */
sealed interface ChangeUiLanguageRemoteDataSourceError : RemoteDataSourceErrorV2 {
    /**
     * Language preference was updated on some devices but not all.
     *
     * Indicates partial success where synchronization failed for some devices.
     */
    data object LanguageNotChangedForAllDevices : ChangeUiLanguageRemoteDataSourceError
}
