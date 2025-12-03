package timur.gilfanov.messenger.ui.screen.user

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createFailingIdentityRepository
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelObservationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `Unauthorized error from observation posts Unauthorized side effect`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createFailingIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectSideEffect(LanguageSideEffects.Unauthorized)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Unknown repository error from observation does not posts side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Failure(
                GetSettingsRepositoryError.UnknownError(Exception("Test error")),
            ),
        )
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
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

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            job.cancelAndJoin()
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

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
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
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                LanguageUiState(
                    languages = persistentListOf(UiLanguage.English, UiLanguage.German),
                    selectedLanguage = UiLanguage.German,
                )
            }

            testScheduler.advanceTimeBy(300)
            testScheduler.runCurrent()
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `change failure does not break observation stream`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
        }
    }
}
