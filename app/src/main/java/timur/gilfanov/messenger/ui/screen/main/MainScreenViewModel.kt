package timur.gilfanov.messenger.ui.screen.main

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@HiltViewModel
class MainScreenViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) :
    ViewModel() {

    private val _state = MutableStateFlow(
        savedStateHandle.get<MainScreenUiState>(SAVED_STATE_KEY) ?: MainScreenUiState(),
    )
    val state = _state.asStateFlow()

    private val tabChannel = Channel<Int>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (index in tabChannel) {
                _state.update { it.copy(selectedTab = index) }
                savedStateHandle[SAVED_STATE_KEY] = _state.value
            }
        }
    }

    fun selectTab(index: Int) {
        tabChannel.trySend(index)
    }

    companion object {
        private const val SAVED_STATE_KEY = "main_screen_ui_state"
    }
}

@Immutable
@Parcelize
data class MainScreenUiState(val selectedTab: Int = 0) : Parcelable
