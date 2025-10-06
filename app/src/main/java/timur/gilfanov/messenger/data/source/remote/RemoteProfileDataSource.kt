package timur.gilfanov.messenger.data.source.remote

import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Remote data source for user profile data.
 *
 * Provides access to profile data and operations on the backend service.
 * Handles network communication and uploads for profile updates.
 */
interface RemoteProfileDataSource {
    /**
     * Retrieves profile from the backend.
     *
     * @param userId The unique identifier of the user
     * @return Success with [Profile] or failure with [RemoteUserDataSourceError]
     */
    suspend fun getProfile(userId: UserId): ResultWithError<Profile, RemoteUserDataSourceError>

    /**
     * Updates user's display name on the backend.
     *
     * @param userId The unique identifier of the user
     * @param name The new display name
     * @return Success or failure with [UpdateNameRemoteDataSourceError]
     */
    suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRemoteDataSourceError>

    /**
     * Uploads and updates user's profile picture on the backend.
     *
     * Emits upload progress updates through the returned Flow, allowing
     * callers to track the upload status.
     *
     * @param userId The unique identifier of the user
     * @param pictureStream Input stream of the picture file
     * @param mimeType MIME type of the picture (e.g., "image/jpeg")
     * @param contentLength Size of the picture file in bytes
     * @return Flow emitting [UploadProgress] updates or errors
     */
    suspend fun updatePicture(
        userId: UserId,
        pictureStream: InputStream,
        mimeType: String,
        contentLength: Long,
    ): Flow<ResultWithError<UploadProgress, UpdatePictureRemoteDataSourceError>>

    /**
     * Removes user's profile picture from the backend.
     *
     * @param userId The unique identifier of the user
     * @param pictureUrl URL of the profile picture to remove
     * @return Success or failure with [RemovePictureRemoteDataSourceError]
     */
    suspend fun removePicture(
        userId: UserId,
        pictureUrl: String,
    ): ResultWithError<Unit, RemovePictureRemoteDataSourceError>
}
