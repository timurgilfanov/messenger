package timur.gilfanov.messenger.ui.screen.settings

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createFailingIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageStoreObservationTest {

    @Test
    fun `Unauthorized error from observation posts Unauthorized side effect`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createFailingIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
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
                GetSettingsRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(Exception("Test error")),
                ),
            ),
        )
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
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
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
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
                copy(selectedLanguage = UiLanguage.German)
            }

            testScheduler.advanceTimeBy(300)
            expectNoItems()

            job.cancelAndJoin()
        }
    }

    @Test
    fun `failure does not break observation stream`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val store = createViewModel(identityRepository, settingsRepository)

        store.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }

            settingsFlow.update {
                ResultWithError.Failure(
                    GetSettingsRepositoryError.LocalOperationFailed(
                        LocalStorageError.UnknownError(Exception("Test error")),
                    ),
                )
            }

            testScheduler.advanceTimeBy(300)
            expectNoItems()

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
