package timur.gilfanov.messenger.domain.usecase.profile.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Repository for managing user profile data.
 *
 * Provides access to user profile information and operations for updating
 * profile properties such as display name and profile picture. Coordinates
 * between remote and local data sources to ensure data consistency.
 */
interface ProfileRepository {
    /**
     * Observes the authenticated user's global profile.
     *
     * @param userKey Opaque local key identifying the authenticated user's cache partition
     * @return Flow emitting profile updates or errors
     */
    fun observeCurrentProfile(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Profile, ObserveProfileRepositoryError>>

    /**
     * Updates user's display name.
     *
     * @param userId The unique identifier of the user
     * @param name The new display name
     * @return Success or failure with [UpdateNameRepositoryError]
     */
    suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRepositoryError>

    /**
     * Updates user's profile picture.
     *
     * @param userId The unique identifier of the user
     * @param pictureUri URI pointing to the new profile picture
     * @return Success or failure with [UpdatePictureRepositoryError]
     */
    suspend fun updatePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, UpdatePictureRepositoryError>

    /**
     * Removes user's profile picture.
     *
     * @param userId The unique identifier of the user
     * @param pictureUri URI of the profile picture to remove
     * @return Success or failure with [RemovePictureRepositoryError]
     */
    suspend fun removePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, RemovePictureRepositoryError>
}
