package timur.gilfanov.messenger.ui.screen.settings

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSettingsRepositoryWithLanguage
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageStoreTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageStoreInitialStateTest {

    @Test
    fun `first state update contains English and German languages with selected from repository`() =
        runTest {
            val identityRepository = createSuccessfulIdentityRepository()
            val settingsRepository = createSettingsRepositoryWithLanguage(UiLanguage.English)
            val store = createViewModel(identityRepository, settingsRepository)

            store.test(this) {
                val job = runOnCreate()

                expectState(
                    LanguageUiState(
                        languages = persistentListOf(UiLanguage.English, UiLanguage.German),
                        selectedLanguage = UiLanguage.English,
                    ),
                )

                job.cancelAndJoin()
            }
        }
}
