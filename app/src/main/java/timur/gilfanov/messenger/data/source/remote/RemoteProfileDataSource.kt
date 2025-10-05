package timur.gilfanov.messenger.data.source.remote

import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

interface RemoteProfileDataSource {
    suspend fun getProfile(userId: UserId): ResultWithError<Profile, RemoteUserDataSourceError>

    suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRemoteDataSourceError>

    suspend fun updatePicture(
        userId: UserId,
        pictureStream: InputStream,
        mimeType: String,
        contentLength: Long,
    ): Flow<ResultWithError<UploadProgress, UpdatePictureRemoteDataSourceError>>

    suspend fun removePicture(
        userId: UserId,
        pictureUrl: String,
    ): ResultWithError<Unit, RemovePictureRemoteDataSourceError>
}
