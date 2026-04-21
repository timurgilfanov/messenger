package timur.gilfanov.messenger.ui.screen.settings

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
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createFailingIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelObservationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private companion object {
        const val DEBOUNCE_PASS_MS = 201L
        const val NO_UPDATE_WINDOW_MS = 300L
    }

    @Test
    fun `Unauthorized error from observation does not post side effect`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createFailingIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(DEBOUNCE_PASS_MS)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Unknown repository error from observation does not posts side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Failure(
                GetSettingsRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(Exception("Test error")),
                ),
            ),
        )
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(DEBOUNCE_PASS_MS)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectedLanguage updates correctly via observation`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rapid state changes maintain consistency`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            advanceTimeBy(NO_UPDATE_WINDOW_MS)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure does not break observation stream`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            settingsFlow.update {
                ResultWithError.Failure(
                    GetSettingsRepositoryError.LocalOperationFailed(
                        LocalStorageError.UnknownError(Exception("Test error")),
                    ),
                )
            }

            advanceTimeBy(NO_UPDATE_WINDOW_MS)
            expectNoEvents()

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
