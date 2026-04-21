package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.auth.domain.usecase.LogoutError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

sealed interface SettingsSideEffects {
    data class ObserveSettingsFailed(val error: LocalStorageError) : SettingsSideEffects
    data class LogoutFailed(val error: LogoutError) : SettingsSideEffects
}
