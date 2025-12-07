package timur.gilfanov.messenger.ui.screen.user

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val profile: ProfileUi) : ProfileUiState
}
