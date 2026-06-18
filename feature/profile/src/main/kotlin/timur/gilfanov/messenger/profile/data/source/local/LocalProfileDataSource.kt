package timur.gilfanov.messenger.profile.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile

/**
 * Local data source for user profile data.
 *
 * Provides access to locally cached profile information. Acts as the
 * single source of truth for profile data in the app, with updates
 * synchronized from remote data source.
 */
interface LocalProfileDataSource {
    /**
     * Observes profile changes for a specific user scope.
     *
     * @param userKey Opaque key identifying the authenticated user's local cache partition
     * @return Flow emitting profile updates or errors
     */
    fun observeProfile(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Profile, ProfileLocalDataSourceError>>

    /**
     * Inserts or replaces the cached profile for a user scope.
     *
     * @param userKey Opaque key identifying the authenticated user's local cache partition
     * @param profile Profile to cache locally
     * @return Success or failure with [ProfileLocalDataSourceError]
     */
    suspend fun upsertProfile(
        userKey: UserScopeKey,
        profile: Profile,
    ): ResultWithError<Unit, ProfileLocalDataSourceError>

    /**
     * Deletes all cached profile data for a user scope.
     *
     * @param userKey Opaque key identifying the authenticated user's local cache partition
     * @return Success or failure with [ProfileLocalDataSourceError]
     */
    suspend fun deleteProfile(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, ProfileLocalDataSourceError>
}
