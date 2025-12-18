package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.usecase.user.ObserveAndApplyLocaleUseCase

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
) : ViewModel() {
    init {
        viewModelScope.launch {
            observeAndApplyLocale().collect { }
        }
    }
}
