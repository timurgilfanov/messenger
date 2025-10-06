package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Local data source for user profile data.
 *
 * Provides access to locally cached profile information. Acts as the
 * single source of truth for profile data in the app, with updates
 * synchronized from remote data source.
 */
interface LocalProfileDataSource {
    /**
     * Observes profile changes for a specific user.
     *
     * @param userId The unique identifier of the user to observe
     * @return Flow emitting profile updates or errors
     */
    fun observeProfile(userId: UserId): Flow<ResultWithError<Profile, LocalUserDataSourceError>>

    /**
     * Updates profile using a transformation function.
     *
     * Atomically reads the current profile, applies the transformation,
     * and writes the result back to local storage.
     *
     * @param userId The unique identifier of the user
     * @param transform Function transforming the current profile to new profile
     * @return Success or failure with [LocalUserDataSourceError]
     */
    suspend fun updateProfile(
        userId: UserId,
        transform: (Profile) -> Profile,
    ): ResultWithError<Unit, LocalUserDataSourceError>
}
