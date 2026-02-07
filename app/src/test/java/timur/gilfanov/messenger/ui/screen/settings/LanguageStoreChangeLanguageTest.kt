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
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSettingsRepositoryFake
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageStoreChangeLanguageTest {

    @Test
    fun `changeLanguage successfully completes without errors`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)

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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()
            val initialState = awaitState()
            assertEquals(UiLanguage.English, initialState.selectedLanguage)

            store.changeLanguage(UiLanguage.English)

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `changing from German to English works`() = runTest {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.German)
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            store.changeLanguage(UiLanguage.English)

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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            store.changeLanguage(UiLanguage.English)
            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)
            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }

            job.cancelAndJoin()
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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)

            val sideEffect = assertIs<LanguageSideEffects.ChangeFailed>(awaitSideEffect())
            assertIs<LocalStorageError.StorageFull>(sideEffect.error)

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)

            val sideEffect = awaitSideEffect()
            assertIs<LanguageSideEffects.ChangeFailed>(sideEffect)
            assertIs<LocalStorageError.Corrupted>(sideEffect.error)

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            store.changeLanguage(UiLanguage.German)

            val sideEffect = awaitSideEffect()
            assertIs<LanguageSideEffects.ChangeFailed>(sideEffect)
            val unknownError = assertIs<LocalStorageError.UnknownError>(sideEffect.error)
            assertEquals(testException, unknownError.cause)

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
//        val store = createViewModel(identityRepository, settingsRepository)
//
//        store.test(this) {
//            val job = runOnCreate()
//
//            expectState {
//                copy(selectedLanguage = UiLanguage.English)
//            }
//
//            store.changeLanguage(UiLanguage.German)
//
//            val sideEffect = awaitSideEffect()
//            assertIs<LanguageSideEffects.Unauthorized>(sideEffect)
//
//            job.cancelAndJoin()
//        }
//    }
}
