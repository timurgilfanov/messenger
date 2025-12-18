package timur.gilfanov.messenger.ui.activity

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import timur.gilfanov.messenger.domain.usecase.user.ObserveAndApplyLocaleUseCase

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val observeAndApplyLocale: ObserveAndApplyLocaleUseCase,
) : ViewModel() {
    suspend fun observeAndApplyLocaleChange() {
        observeAndApplyLocale().collect { }
    }
}
