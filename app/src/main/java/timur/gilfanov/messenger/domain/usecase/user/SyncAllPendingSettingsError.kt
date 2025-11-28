package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError

sealed interface SyncAllPendingSettingsError {
    data object IdentityNotAvailable : SyncAllPendingSettingsError
    data class SyncFailed(val error: SyncAllSettingsRepositoryError) : SyncAllPendingSettingsError
}
