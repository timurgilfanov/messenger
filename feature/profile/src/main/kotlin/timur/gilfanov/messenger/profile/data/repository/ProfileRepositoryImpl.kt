package timur.gilfanov.messenger.profile.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository
import timur.gilfanov.messenger.domain.usecase.profile.repository.RemovePictureRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdateNameRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdatePictureRepositoryError
import timur.gilfanov.messenger.profile.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSource
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSource
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSourceError
import timur.gilfanov.messenger.profile.data.source.remote.toRemoteError

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val localDataSource: LocalProfileDataSource,
    private val remoteDataSource: RemoteProfileDataSource,
) : ProfileRepository {

    override fun observeCurrentProfile(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Profile, ObserveProfileRepositoryError>> = flow {
        val firstLocal = localDataSource.observeProfile(userKey).first()
        var hasCachedProfile = false

        when (firstLocal) {
            is ResultWithError.Success -> {
                hasCachedProfile = true
                emit(ResultWithError.Success(firstLocal.data))
            }

            is ResultWithError.Failure -> when (firstLocal.error) {
                ProfileLocalDataSourceError.UserDataNotFound -> Unit
                is ProfileLocalDataSourceError.LocalDataSource -> {
                    emit(ResultWithError.Failure(firstLocal.error.toObserveProfileError()))
                    return@flow
                }
            }
        }

        when (val remoteResult = remoteDataSource.getCurrentProfile()) {
            is ResultWithError.Success -> {
                val upsertResult = localDataSource.upsertProfile(
                    userKey = userKey,
                    profile = remoteResult.data,
                )
                when (upsertResult) {
                    is ResultWithError.Success -> {
                        emit(ResultWithError.Success(remoteResult.data))
                    }

                    is ResultWithError.Failure -> {
                        emit(ResultWithError.Failure(upsertResult.error.toObserveProfileError()))
                    }
                }
            }

            is ResultWithError.Failure -> {
                if (!hasCachedProfile) {
                    emit(ResultWithError.Failure(remoteResult.error.toObserveProfileError()))
                }
            }
        }

        emitAll(
            localDataSource.observeProfile(userKey)
                .drop(1)
                .map { result ->
                    result.bimap(
                        onSuccess = { it },
                        onFailure = ProfileLocalDataSourceError::toObserveProfileError,
                    )
                },
        )
    }

    override suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRepositoryError> = ResultWithError.Failure(
        UpdateNameRepositoryError.RemoteOperationFailed(editOutOfScopeError()),
    )

    override suspend fun updatePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, UpdatePictureRepositoryError> = ResultWithError.Failure(
        UpdatePictureRepositoryError.RemoteOperationFailed(editOutOfScopeError()),
    )

    override suspend fun removePicture(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, RemovePictureRepositoryError> = ResultWithError.Failure(
        RemovePictureRepositoryError.RemoteOperationFailed(editOutOfScopeError()),
    )

    private fun editOutOfScopeError(): RemoteError = RemoteError.Failed.UnknownServiceError(
        ErrorReason("Profile edit operations are out of scope for profile retrieval"),
    )
}

private fun ProfileLocalDataSourceError.toObserveProfileError(): ObserveProfileRepositoryError =
    ObserveProfileRepositoryError.LocalOperationFailed(toLocalStorageError())

private fun RemoteProfileDataSourceError.toObserveProfileError(): ObserveProfileRepositoryError =
    ObserveProfileRepositoryError.RemoteOperationFailed(toRemoteError())

private fun ProfileLocalDataSourceError.toLocalStorageError(): LocalStorageError = when (this) {
    ProfileLocalDataSourceError.UserDataNotFound ->
        LocalStorageError.UnknownError(IllegalStateException("Profile cache row not found"))

    is ProfileLocalDataSourceError.LocalDataSource -> error.toLocalStorageError()
}

private fun LocalDataSourceErrorV2.toLocalStorageError(): LocalStorageError = when (this) {
    is LocalDataSourceErrorV2.ReadError,
    is LocalDataSourceErrorV2.WriteError,
    -> LocalStorageError.TemporarilyUnavailable

    is LocalDataSourceErrorV2.SerializationError ->
        LocalStorageError.UnknownError(IllegalStateException(reason.value))

    is LocalDataSourceErrorV2.DeserializationError ->
        LocalStorageError.UnknownError(IllegalStateException(reason.value))
}
