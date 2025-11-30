package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError

sealed interface SyncSettingError {
    data object IdentityNotAvailable : SyncSettingError
    data class SyncFailed(val error: SyncSettingRepositoryError) : SyncSettingError
}
