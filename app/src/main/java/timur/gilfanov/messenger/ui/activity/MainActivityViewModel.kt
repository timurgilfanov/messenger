package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _initialDestination = MutableStateFlow<NavKey?>(null)
    val initialDestination = _initialDestination.asStateFlow()

    init {
        viewModelScope.launch {
            observeAndApplyLocale().collect {
                logger.d(TAG, "Locale update applied")
            }
        }
        viewModelScope.launch {
            _initialDestination.value = when (authRepository.authState.first()) {
                is AuthState.Authenticated -> Main
                AuthState.Unauthenticated -> Login
            }
        }
    }

    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}
