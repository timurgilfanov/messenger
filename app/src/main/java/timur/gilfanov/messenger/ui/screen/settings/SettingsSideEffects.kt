package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

sealed interface SettingsSideEffects {
    data object Unauthorized : SettingsSideEffects
    data class ObserveSettingsFailed(val error: LocalStorageError) : SettingsSideEffects
}
