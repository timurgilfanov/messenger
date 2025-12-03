package timur.gilfanov.messenger.ui.screen.user

import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSettingsRepositoryWithChangeError
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelChangeLanguageTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `changeLanguage successfully completes without errors`() = runTest {
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

            viewModel.changeLanguage(UiLanguage.German)
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `changing to same language does not update state`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()
            val initialState = awaitState()
            assertEquals(UiLanguage.English, initialState.selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }

            testScheduler.advanceTimeBy(300)
            testScheduler.runCurrent()
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `changing from German to English works`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.German)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            viewModel.changeLanguage(UiLanguage.English)
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
    fun `multiple consecutive language changes process correctly`() = runTest {
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

            viewModel.changeLanguage(UiLanguage.German)
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            viewModel.changeLanguage(UiLanguage.English)
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.English))
            }
            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)
            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository InsufficientStorage posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithChangeError(
            currentLanguage = UiLanguage.English,
            changeError = ChangeLanguageRepositoryError.Recoverable.InsufficientStorage,
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitSideEffect())
            assertIs<ChangeLanguageRepositoryError.Recoverable.InsufficientStorage>(
                sideEffect.error,
            )

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository DataCorruption posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithChangeError(
            currentLanguage = UiLanguage.English,
            changeError = ChangeLanguageRepositoryError.Recoverable.DataCorruption,
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)

            val sideEffect = awaitSideEffect()
            assertIs<LanguageSideEffects.ChangeFailed>(sideEffect)
            assertIs<ChangeLanguageRepositoryError.Recoverable.DataCorruption>(sideEffect.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository UnknownError posts ChangeFailed side effect`() = runTest {
        val testException = RuntimeException("Test exception")
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithChangeError(
            currentLanguage = UiLanguage.English,
            changeError = ChangeLanguageRepositoryError.UnknownError(testException),
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)

            val sideEffect = awaitSideEffect()
            assertIs<LanguageSideEffects.ChangeFailed>(sideEffect)
            assertIs<ChangeLanguageRepositoryError.UnknownError>(sideEffect.error)
            assertEquals(testException, sideEffect.error.cause)

            job.cancelAndJoin()
        }
    }

//    @Test
//    fun `Unauthorized error posts Unauthorized side effect`() = runTest {
//        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
//            ResultWithError.Success(createTestSettings(UiLanguage.English)),
//        )
//          todo need change from success to failure for this test
//        val identityRepository = createFailingIdentityRepository()
//        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
//        val viewModel = createViewModel(identityRepository, settingsRepository)
//
//        viewModel.test(this) {
//            val job = runOnCreate()
//
//            expectState {
//                copy(selectedLanguage = UiLanguage.English)
//            }
//
//            viewModel.changeLanguage(UiLanguage.German)
//
//            val sideEffect = awaitSideEffect()
//            assertIs<LanguageSideEffects.Unauthorized>(sideEffect)
//
//            job.cancelAndJoin()
//        }
//    }
}
