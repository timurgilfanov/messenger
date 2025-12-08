package timur.gilfanov.messenger.ui.screen.settings

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryFake
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelChangeLanguageTest {

    @Test
    fun `changeLanguage successfully completes without errors`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `changing to same language does not update state`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()
            val initialState = awaitState()
            assertEquals(UiLanguage.English, initialState.selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `changing from German to English works`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.German)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            viewModel.changeLanguage(UiLanguage.English)

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `multiple consecutive language changes process correctly`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            viewModel.changeLanguage(UiLanguage.English)
            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            viewModel.changeLanguage(UiLanguage.German)
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository InsufficientStorage posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.Recoverable.InsufficientStorage,
            ),
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

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository DataCorruption posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.Recoverable.DataCorruption,
            ),
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

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ChangeLanguageRepository UnknownError posts ChangeFailed side effect`() = runTest {
        val testException = RuntimeException("Test exception")
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.UnknownError(testException),
            ),
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

            testScheduler.advanceTimeBy(300)
            expectNoItems()

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
