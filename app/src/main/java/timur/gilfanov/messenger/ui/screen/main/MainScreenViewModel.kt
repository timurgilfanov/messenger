package timur.gilfanov.messenger.ui.screen.main

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@HiltViewModel
class MainScreenViewModel @Inject constructor() :
    ViewModel(),
    ContainerHost<MainScreenUiState, Nothing> {

    override val container = container<MainScreenUiState, Nothing>(MainScreenUiState())

    fun selectTab(index: Int) = intent {
        reduce { state.copy(selectedTab = index) }
    }
}

@Immutable
data class MainScreenUiState(val selectedTab: Int = 0)
