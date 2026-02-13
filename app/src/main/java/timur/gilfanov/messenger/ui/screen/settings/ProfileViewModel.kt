package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
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
import timur.gilfanov.messenger.ui.repeatOnSubscription
import timur.gilfanov.messenger.util.Logger

private const val TAG = "ProfileViewModel"
private const val KEY_PROFILE = "profile"

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        savedStateHandle.get<ProfileUi>(KEY_PROFILE)?.let { ProfileUiState.Ready(it) }
            ?: ProfileUiState.Loading,
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<ProfileSideEffects>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                observeProfile().collect {
                    it.onSuccess { profile ->
                        val profileUi = profile.toProfileUi()
                        _state.value = ProfileUiState.Ready(profileUi)
                        savedStateHandle[KEY_PROFILE] = profileUi
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
}
