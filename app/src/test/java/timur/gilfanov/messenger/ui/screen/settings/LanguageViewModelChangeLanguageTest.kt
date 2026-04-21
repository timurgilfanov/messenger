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

    private companion object {
        const val DEBOUNCE_PASS_MS = 201L
        const val NO_UPDATE_WINDOW_MS = 301L
    }

    @Test
    fun `changeLanguage successfully completes without errors`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(DEBOUNCE_PASS_MS)
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
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(NO_UPDATE_WINDOW_MS)
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
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(DEBOUNCE_PASS_MS)
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
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.English)
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            advanceTimeBy(DEBOUNCE_PASS_MS)
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
            advanceTimeBy(DEBOUNCE_PASS_MS)

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
            advanceTimeBy(DEBOUNCE_PASS_MS)

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
            advanceTimeBy(DEBOUNCE_PASS_MS)

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())
            val unknownError = assertIs<LocalStorageError.UnknownError>(sideEffect.error)
            assertEquals(testException, unknownError.cause)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed change with same language can be retried`() = runTest {
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
            advanceTimeBy(DEBOUNCE_PASS_MS)

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()
            assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())

            viewModel.changeLanguage(UiLanguage.German)
            advanceUntilIdle()
            assertIs<LanguageSideEffects.ChangeFailed>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rapid taps only last language change is applied`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)

            viewModel.changeLanguage(UiLanguage.German)
            viewModel.changeLanguage(UiLanguage.English)
            advanceUntilIdle()
            advanceTimeBy(DEBOUNCE_PASS_MS)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
