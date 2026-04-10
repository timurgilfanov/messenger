package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.util.Logger
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAndApplyLocale().collect {
                logger.d(TAG, "Locale update applied")
            }
        }
        viewModelScope.launch {
            val authState = authRepository.authState.first()
            _uiState.update {
                MainActivityUiState.Ready(
                    initialDestination = when (authState) {
                        is AuthState.Authenticated -> Main
                        AuthState.Unauthenticated -> Login
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}
