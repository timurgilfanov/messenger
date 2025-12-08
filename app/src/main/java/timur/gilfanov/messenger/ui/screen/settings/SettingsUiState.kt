package timur.gilfanov.messenger.ui.screen.settings

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val settings: SettingsUi) : SettingsUiState
}
