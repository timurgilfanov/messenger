package timur.gilfanov.messenger.profile.data.source.remote

import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Test double for [RemoteProfileDataSource] returning a preconfigured profile.
 *
 * Only [getCurrentProfile] is implemented; the mutating operations are not used by
 * the profile retrieval flow and fail fast if called.
 */
class RemoteProfileDataSourceFake(private val profile: Profile) : RemoteProfileDataSource {

    override suspend fun getCurrentProfile() =
        ResultWithError.Success<Profile, RemoteProfileDataSourceError>(profile)

    override suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRemoteDataSourceError> =
        error("RemoteProfileDataSourceFake.updateName not implemented")

    override suspend fun updatePicture(
        userId: UserId,
        pictureStream: InputStream,
        mimeType: String,
        contentLength: Long,
    ): Flow<ResultWithError<UploadProgress, UpdatePictureRemoteDataSourceError>> =
        error("RemoteProfileDataSourceFake.updatePicture not implemented")

    override suspend fun removePicture(
        userId: UserId,
        pictureUrl: String,
    ): ResultWithError<Unit, RemovePictureRemoteDataSourceError> =
        error("RemoteProfileDataSourceFake.removePicture not implemented")
}
