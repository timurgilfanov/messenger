package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.settings.repository.ObserveSettingsRepositoryError

sealed interface SettingsSideEffects {
    data object Unauthorized : SettingsSideEffects
    data class ObserveSettingsFailed(val error: ObserveSettingsRepositoryError) :
        SettingsSideEffects
}
