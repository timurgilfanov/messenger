package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

sealed interface SyncSettingError {
    data object IdentityNotAvailable : SyncSettingError
    data class SyncFailed(val error: SyncSettingRepositoryError) : SyncSettingError
}
