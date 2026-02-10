package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state = _state.asStateFlow()

    private val _effects = Channel<ProfileSideEffects>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeProfile().collect {
                it.onSuccess { profile ->
                    _state.value = ProfileUiState.Ready(profile.toProfileUi())
                }.onFailure { error ->
                    when (error) {
                        ObserveProfileError.Unauthorized -> {
                            logger.i(
                                TAG,
                                "Profile observation failed with Unauthorized error",
                            )
                            _effects.send(ProfileSideEffects.Unauthorized)
                        }
                    }
                }
            }
        }
    }
}
