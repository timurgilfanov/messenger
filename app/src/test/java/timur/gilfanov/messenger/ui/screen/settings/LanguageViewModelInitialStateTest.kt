package timur.gilfanov.messenger.ui.screen.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryWithLanguage
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(ExperimentalCoroutinesApi::class)
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

            viewModel.state.test {
                val initial = awaitItem()
                assertNull(initial.selectedLanguage)
                assertEquals(
                    persistentListOf(UiLanguage.English, UiLanguage.German),
                    initial.languages,
                )

                advanceTimeBy(201)
                val updated = awaitItem()
                assertEquals(UiLanguage.English, updated.selectedLanguage)
                assertEquals(
                    persistentListOf(UiLanguage.English, UiLanguage.German),
                    updated.languages,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}
