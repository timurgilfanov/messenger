package timur.gilfanov.messenger.domain.usecase.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository
import timur.gilfanov.messenger.domain.usecase.profile.repository.RemovePictureRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdateNameRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdatePictureRepositoryError

class ProfileRepositoryStub(
    private val flow: Flow<ResultWithError<Profile, ObserveProfileRepositoryError>> = emptyFlow(),
) : ProfileRepository {
    override fun observeCurrentProfile(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Profile, ObserveProfileRepositoryError>> = flow

    override suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRepositoryError> =
        error("ProfileRepositoryStub.updateName not implemented")

    override suspend fun updatePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, UpdatePictureRepositoryError> =
        error("ProfileRepositoryStub.updatePicture not implemented")

    override suspend fun removePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, RemovePictureRepositoryError> =
        error("ProfileRepositoryStub.removePicture not implemented")
}
