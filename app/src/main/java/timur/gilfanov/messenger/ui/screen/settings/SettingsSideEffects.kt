package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveSettingsRepositoryError

sealed interface SettingsSideEffects {
    data object Unauthorized : SettingsSideEffects
    data class GetSettingsFailed(val error: ObserveSettingsRepositoryError) : SettingsSideEffects
}
