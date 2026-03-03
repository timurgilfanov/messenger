package timur.gilfanov.messenger.ui.screen.settings

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
sealed interface SettingsUiState : Parcelable {
    @Parcelize
    data object Loading : SettingsUiState

    @Parcelize
    data class Ready(val settings: SettingsUi) : SettingsUiState
}
