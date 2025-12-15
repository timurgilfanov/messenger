package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val settings: SettingsUi) : SettingsUiState
}
