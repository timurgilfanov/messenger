package timur.gilfanov.messenger.profile.ui.screen

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val profile: ProfileUi) : ProfileUiState
}
