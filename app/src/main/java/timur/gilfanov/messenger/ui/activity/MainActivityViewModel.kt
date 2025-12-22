package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
    private val logger: Logger,
) : ViewModel() {
    init {
        viewModelScope.launch {
            observeAndApplyLocale().collect {
                logger.d(TAG, "Locale update applied")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}
