package timur.gilfanov.messenger.ui.screen.main

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.parcelize.Parcelize
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@HiltViewModel
class MainScreenViewModel @Inject constructor(val savedStateHandle: SavedStateHandle) :
    ViewModel(),
    ContainerHost<MainScreenUiState, Nothing> {

    override val container = container<MainScreenUiState, Nothing>(
        initialState = MainScreenUiState(),
        savedStateHandle = savedStateHandle,
    )

    fun selectTab(index: Int) = intent {
        reduce { state.copy(selectedTab = index) }
    }
}

@Immutable
@Parcelize
data class MainScreenUiState(val selectedTab: Int = 0) : Parcelable
