package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCaseNoOp
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsViewModelObservationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    private fun createViewModel(
        observeSettings: ObserveSettingsUseCase,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SettingsViewModel = SettingsViewModel(
        observeSettings = observeSettings,
        logoutUseCase = LogoutUseCaseNoOp(),
        logger = NoOpLogger(),
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings()),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.state.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transitions to Ready state on successful observation`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.state.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            advanceTimeBy(201)
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.English)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sends Unauthorized effect on Unauthorized error`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Failure(ObserveSettingsError.Unauthorized),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(201)
            assertEquals(SettingsSideEffects.Unauthorized, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sends ObserveSettingsFailed effect on LocalOperationFailed error`() = runTest {
        val localStorageError = LocalStorageError.UnknownError(Exception("Test error"))
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Failure(
                ObserveSettingsError.LocalOperationFailed(localStorageError),
            ),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(201)
            assertEquals(SettingsSideEffects.ObserveSettingsFailed(localStorageError), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state updates on subsequent settings changes`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.state.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            advanceTimeBy(201)
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.English)), awaitItem())

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(201)
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.German)), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure does not break observation stream`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.state.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            advanceTimeBy(201)
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.English)), awaitItem())

            val localStorageError = LocalStorageError.UnknownError(Exception("Test error"))
            settingsFlow.update {
                ResultWithError.Failure(
                    ObserveSettingsError.LocalOperationFailed(localStorageError),
                )
            }
            advanceTimeBy(201)
            expectNoEvents()

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(201)
            assertEquals(SettingsUiState.Ready(SettingsUi(UiLanguage.German)), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
