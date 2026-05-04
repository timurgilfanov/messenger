package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCase
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCase
import timur.gilfanov.messenger.util.Logger
import timur.gilfanov.messenger.util.repeatOnSubscription

private const val TAG = "SettingsViewModel"
private const val SAVED_STATE_KEY = "settingsUiState"
private val STATE_UPDATE_DEBOUNCE = 200.milliseconds

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val logger: Logger,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        savedStateHandle.get<SettingsUi>(SAVED_STATE_KEY)?.let { SettingsUiState.Ready(it) }
            ?: SettingsUiState.Loading,
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<SettingsSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun logout() {
        viewModelScope.launch {
            logoutUseCase().fold(
                onSuccess = { logger.i(TAG, "Logout succeeded") },
                onFailure = { error ->
                    logger.i(TAG, "Logout failed: $error")
                    _effects.send(SettingsSideEffects.LogoutFailed(error))
                },
            )
        }
    }

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                observeSettings()
                    .debounce(STATE_UPDATE_DEBOUNCE)
                    .collect { result ->
                        result
                            .onSuccess { settings ->
                                val settingsUi = settings.toSettingsUi()
                                _state.value = SettingsUiState.Ready(settingsUi)
                                savedStateHandle[SAVED_STATE_KEY] = settingsUi
                            }
                            .onFailure { error ->
                                when (error) {
                                    ObserveSettingsError.Unauthorized -> {
                                        logger.i(
                                            TAG,
                                            "Settings observation failed with Unauthorized error",
                                        )
                                    }

                                    ObserveSettingsError.SettingsUnspecified -> {
                                        logger.i(
                                            TAG,
                                            "Settings are unspecified",
                                        )
                                    }

                                    is ObserveSettingsError.LocalOperationFailed -> {
                                        logger.i(
                                            TAG,
                                            "Settings observation failed with local error: ${error.error}",
                                        )
                                        _effects.send(
                                            SettingsSideEffects.ObserveSettingsFailed(error.error),
                                        )
                                    }
                                }
                            }
                    }
            }
        }
    }
}
