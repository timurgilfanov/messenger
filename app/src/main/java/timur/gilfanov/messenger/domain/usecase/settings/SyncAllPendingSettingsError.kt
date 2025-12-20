package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError

sealed interface SyncAllPendingSettingsError {
    data object IdentityNotAvailable : SyncAllPendingSettingsError
    data class SyncFailed(val error: SyncAllSettingsRepositoryError) : SyncAllPendingSettingsError
}
