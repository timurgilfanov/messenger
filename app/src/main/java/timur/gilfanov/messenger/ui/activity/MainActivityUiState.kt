package timur.gilfanov.messenger.ui.activity

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey

@Immutable
sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data class Ready(val initialDestination: NavKey) : MainActivityUiState
}
