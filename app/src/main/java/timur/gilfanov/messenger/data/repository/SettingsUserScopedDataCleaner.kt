package timur.gilfanov.messenger.data.repository

import javax.inject.Inject
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.UserScopedDataCleaner
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.DeleteUserDataRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

class SettingsUserScopedDataCleaner @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : UserScopedDataCleaner {
    override suspend fun deleteUserData(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, LocalStorageError> = settingsRepository.deleteUserData(userKey).bimap(
        onSuccess = { it },
        onFailure = DeleteUserDataRepositoryError::toLocalStorageError,
    )
}

private fun DeleteUserDataRepositoryError.toLocalStorageError(): LocalStorageError = when (this) {
    is DeleteUserDataRepositoryError.LocalOperationFailed -> error
}
