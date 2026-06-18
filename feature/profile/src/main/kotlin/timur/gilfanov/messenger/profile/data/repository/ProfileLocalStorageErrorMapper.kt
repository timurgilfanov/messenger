package timur.gilfanov.messenger.profile.data.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.profile.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError

internal fun ProfileLocalDataSourceError.toLocalStorageError(): LocalStorageError = when (this) {
    ProfileLocalDataSourceError.UserDataNotFound ->
        LocalStorageError.UnknownError(IllegalStateException("Profile cache row not found"))

    is ProfileLocalDataSourceError.LocalDataSource -> error.toLocalStorageError()
}

internal fun LocalDataSourceErrorV2.toLocalStorageError(): LocalStorageError = when (this) {
    is LocalDataSourceErrorV2.ReadError,
    is LocalDataSourceErrorV2.WriteError,
    -> LocalStorageError.TemporarilyUnavailable

    is LocalDataSourceErrorV2.StorageFullError -> LocalStorageError.StorageFull

    is LocalDataSourceErrorV2.ReadOnlyError -> LocalStorageError.ReadOnly

    is LocalDataSourceErrorV2.AccessDeniedError -> LocalStorageError.AccessDenied

    is LocalDataSourceErrorV2.CorruptedError ->
        LocalStorageError.Corrupted

    is LocalDataSourceErrorV2.SerializationError ->
        LocalStorageError.UnknownError(IllegalStateException(reason.value))

    is LocalDataSourceErrorV2.DeserializationError ->
        LocalStorageError.UnknownError(IllegalStateException(reason.value))
}
