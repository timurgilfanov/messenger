package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _effects = Channel<MainActivitySideEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAndApplyLocale().collect {
                logger.d(TAG, "Locale update applied")
            }
        }
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                when (state) {
                    AuthState.Loading -> { /* auth state is being determined, keep loading */ }
                    is AuthState.Authenticated -> {
                        if (_uiState.value is MainActivityUiState.Loading) {
                            _uiState.update {
                                MainActivityUiState.Ready(initialDestination = Main)
                            }
                        }
                    }
                    AuthState.Unauthenticated -> {
                        if (_uiState.value is MainActivityUiState.Loading) {
                            _uiState.update {
                                MainActivityUiState.Ready(initialDestination = Login)
                            }
                        }
                        _effects.send(MainActivitySideEffect.NavigateToLogin)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}
