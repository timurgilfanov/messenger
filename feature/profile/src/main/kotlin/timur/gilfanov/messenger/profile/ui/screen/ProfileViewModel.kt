package timur.gilfanov.messenger.profile.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.profile.ui.screen.ProfileSideEffects.Feedback
import timur.gilfanov.messenger.util.Logger
import timur.gilfanov.messenger.util.repeatOnSubscription

private const val TAG = "ProfileViewModel"
private const val KEY_PROFILE = "profile"
private const val KEY_ERROR_STATE = "error_state"

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        savedStateHandle.get<ProfileUi>(KEY_PROFILE)?.let { ProfileUiState.Ready(it) }
            ?: if (savedStateHandle.get<Boolean>(KEY_ERROR_STATE) == true) {
                ProfileUiState.Error
            } else {
                ProfileUiState.Loading
            },
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<ProfileSideEffects>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                retryTrigger.flatMapLatest { _ ->
                    observeProfile()
                }.collect {
                    it.onSuccess { profile ->
                        val profileUi = profile.toProfileUi()
                        _state.value = ProfileUiState.Ready(profileUi)
                        savedStateHandle[KEY_PROFILE] = profileUi
                        savedStateHandle.remove<Boolean>(KEY_ERROR_STATE)
                    }.onFailure { error ->
                        when (error) {
                            ObserveProfileError.Unauthorized -> {
                                logger.i(
                                    TAG,
                                    "Profile observation failed with Unauthorized error",
                                )
                                _state.value = ProfileUiState.Loading
                                savedStateHandle.remove<ProfileUi>(KEY_PROFILE)
                                savedStateHandle.remove<Boolean>(KEY_ERROR_STATE)
                            }

                            is ObserveProfileError.LocalOperationFailed -> {
                                if (_state.value is ProfileUiState.Loading) {
                                    _state.value = ProfileUiState.Error
                                    savedStateHandle[KEY_ERROR_STATE] = true
                                }
                                _effects.send(Feedback.ObserveFailed)
                            }

                            is ObserveProfileError.RemoteOperationFailed -> {
                                logger.i(
                                    TAG,
                                    "Profile observation remote failure: ${error.error}",
                                )
                                if (_state.value is ProfileUiState.Loading) {
                                    _state.value = ProfileUiState.Error
                                    savedStateHandle[KEY_ERROR_STATE] = true
                                }
                                _effects.send(Feedback.ObserveFailed)
                            }
                        }
                    }
                }
            }
        }
    }

    fun retry() {
        if (_state.value is ProfileUiState.Error) {
            _state.value = ProfileUiState.Loading
            savedStateHandle.remove<Boolean>(KEY_ERROR_STATE)
            retryTrigger.value++
        }
    }
}
