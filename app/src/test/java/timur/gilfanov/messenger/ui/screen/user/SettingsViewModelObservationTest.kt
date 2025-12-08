package timur.gilfanov.messenger.ui.screen.user

import kotlin.test.assertIs
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsUseCaseStub
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsViewModelObservationTest {

    fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    fun createViewModel(observeSettings: ObserveSettingsUseCase): SettingsViewModel =
        SettingsViewModel(
            observeSettings = observeSettings,
            logger = NoOpLogger(),
        )

    @Test
    fun `transitions to Ready state on successful observation`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.test(this) {
            val job = runOnCreate()

            expectState { SettingsUiState.Ready(SettingsUi(UiLanguage.English)) }

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Unauthorized error posts Unauthorized side effect`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Failure(ObserveSettingsError.Unauthorized),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.test(this) {
            val job = runOnCreate()

            expectSideEffect(SettingsSideEffects.Unauthorized)

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Repository error posts GetSettingsFailed side effect`() = runTest {
        val repositoryError = GetSettingsRepositoryError.UnknownError(Exception("Test error"))
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Failure(
                ObserveSettingsError.ObserveSettingsRepository(repositoryError),
            ),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.test(this) {
            val job = runOnCreate()

            expectSideEffect(SettingsSideEffects.GetSettingsFailed(repositoryError))

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `state updates via flow observation`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.test(this) {
            val job = runOnCreate()

            expectState { SettingsUiState.Ready(SettingsUi(UiLanguage.English)) }

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            expectState {
                assertIs<SettingsUiState.Ready>(this)
                copy(settings = settings.copy(language = UiLanguage.German))
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `failure does not break observation stream`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val viewModel = createViewModel(ObserveSettingsUseCaseStub(settingsFlow))

        viewModel.test(this) {
            val job = runOnCreate()

            expectState { SettingsUiState.Ready(SettingsUi(UiLanguage.English)) }

            val repositoryError =
                GetSettingsRepositoryError.UnknownError(Exception("Test error"))
            settingsFlow.update {
                ResultWithError.Failure(
                    ObserveSettingsError.ObserveSettingsRepository(repositoryError),
                )
            }

            expectSideEffect(SettingsSideEffects.GetSettingsFailed(repositoryError))

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                assertIs<SettingsUiState.Ready>(this)
                copy(settings = settings.copy(language = UiLanguage.German))
            }

            job.cancelAndJoin()
        }
    }
}
