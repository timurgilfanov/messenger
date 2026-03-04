package timur.gilfanov.messenger.ui.screen.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryFake
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelChangeLanguageTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `changeLanguage successfully completes without errors`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing to same language does not update state`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(301)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing from German to English works`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.German)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple consecutive language changes process correctly`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LocalStorageError StorageFull posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(201)

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())
            assertIs<LocalStorageError.StorageFull>(sideEffect.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LocalStorageError Corrupted posts ChangeFailed side effect`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(201)

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())
            assertIs<LocalStorageError.Corrupted>(sideEffect.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LocalStorageError UnknownError posts ChangeFailed side effect`() = runTest {
        val testException = RuntimeException("Test exception")
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(testException),
                ),
            ),
        )
        val viewModel = createViewModel(identityRepository, settingsRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            advanceTimeBy(201)

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())
            val unknownError = assertIs<LocalStorageError.UnknownError>(sideEffect.error)
            assertEquals(testException, unknownError.cause)

            cancelAndIgnoreRemainingEvents()
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
//        viewModel.state.test {
//            awaitItem()
//            advanceTimeBy(201)
//            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)
//
//            viewModel.changeLanguage(UiLanguage.German)
//
//            val sideEffect = awaitItem() // effects
//            assertIs<LanguageSideEffects.Unauthorized>(sideEffect)
//        }
//    }
}
