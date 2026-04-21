package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
    authRepository: AuthRepository,
    private val logger: Logger,
) : ViewModel() {

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
                _effects.send(
                    when (state) {
                        is AuthState.Authenticated -> MainActivitySideEffect.NavigateToMain
                        AuthState.Unauthenticated -> MainActivitySideEffect.NavigateToLogin
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}
