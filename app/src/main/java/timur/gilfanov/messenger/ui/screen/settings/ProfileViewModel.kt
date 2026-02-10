package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.util.Logger

@Suppress("UnusedPrivateProperty")
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
) : ViewModel() {

    private var _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state = _state.asStateFlow()

    private val _effects = Channel<ProfileSideEffects>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
}
