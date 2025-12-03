package timur.gilfanov.messenger.ui.screen.user

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSettingsRepositoryWithLanguage
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelInitialStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `first state update contains English and German languages with selected from repository`() =
        runTest {
            val identityRepository = createSuccessfulIdentityRepository()
            val settingsRepository = createSettingsRepositoryWithLanguage(UiLanguage.English)
            val viewModel = createViewModel(identityRepository, settingsRepository)

            viewModel.test(this) {
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
