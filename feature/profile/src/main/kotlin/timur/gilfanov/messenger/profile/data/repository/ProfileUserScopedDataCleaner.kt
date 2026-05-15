package timur.gilfanov.messenger.profile.data.repository

import javax.inject.Inject
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.UserScopedDataCleaner
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.profile.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSource
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError

class ProfileUserScopedDataCleaner @Inject constructor(
    private val localDataSource: LocalProfileDataSource,
) : UserScopedDataCleaner {
    override suspend fun deleteUserData(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, LocalStorageError> = localDataSource.deleteProfile(userKey).bimap(
        onSuccess = { it },
        onFailure = ProfileLocalDataSourceError::toLocalStorageError,
    )
}

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
