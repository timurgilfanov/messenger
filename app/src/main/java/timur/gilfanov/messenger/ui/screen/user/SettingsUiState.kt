package timur.gilfanov.messenger.ui.screen.user

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val settings: SettingsUi) : SettingsUiState
}
