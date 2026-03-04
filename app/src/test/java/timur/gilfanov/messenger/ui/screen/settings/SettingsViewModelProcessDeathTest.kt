package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Component::class)
class SettingsViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restores settings from SavedStateHandle after process death`() = runTest {
        val restoredState = SavedStateHandle(
            mapOf("settingsUiState" to SettingsUi(UiLanguage.German)),
        )
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)),
        )
        val viewModel = SettingsViewModel(
            observeSettings = ObserveSettingsUseCaseStub(settingsFlow),
            logger = NoOpLogger(),
            savedStateHandle = restoredState,
        )

        viewModel.state.test {
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.German)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves settings to SavedStateHandle when state becomes Ready`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)),
        )
        val viewModel = SettingsViewModel(
            observeSettings = ObserveSettingsUseCaseStub(settingsFlow),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        backgroundScope.launch { viewModel.state.collect {} }
        advanceTimeBy(201)

        assertEquals(
            SettingsUi(UiLanguage.English),
            savedStateHandle.get<SettingsUi>("settingsUiState"),
        )
    }
}
