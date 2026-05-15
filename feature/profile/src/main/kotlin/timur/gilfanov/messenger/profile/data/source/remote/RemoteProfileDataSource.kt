package timur.gilfanov.messenger.profile.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile

/**
 * Remote data source for user profile data.
 *
 * Provides access to the authenticated user's profile on the backend service.
 */
interface RemoteProfileDataSource {
    /**
     * Retrieves the authenticated user's profile from the backend.
     *
     * @return Success with [Profile] or failure with [RemoteProfileDataSourceError]
     */
    suspend fun getCurrentProfile(): ResultWithError<Profile, RemoteProfileDataSourceError>
}
