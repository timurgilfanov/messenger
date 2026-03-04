package timur.gilfanov.messenger.ui.screen.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelStateTransitionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Verifies that the `languages` list maintains referential equality across state changes.
     * This avoids unnecessary allocations and Compose recompositions.
     */
    @Test
    fun `languages list remains constant throughout lifecycle`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)
            val languages1 = viewModel.state.value.languages

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)
            val languages2 = viewModel.state.value.languages

            assertSame(languages1, languages2)
            assertEquals(
                persistentListOf(UiLanguage.English, UiLanguage.German),
                languages2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Verifies that state updates create new instances, preserving previous state values.
     * This ensures MVI immutability: state1 remains unchanged after state2 is emitted.
     */
    @Test
    fun `state immutability preserved data class copy creates new instance`() = runTest {
        val settingsFlow = MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryWithFlow(settingsFlow)
        val viewModel = createViewModel(identityRepository, settingsRepository)

        viewModel.state.test {
            awaitItem()
            advanceTimeBy(201)
            assertEquals(UiLanguage.English, awaitItem().selectedLanguage)
            val state1 = viewModel.state.value

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }
            advanceTimeBy(201)
            assertEquals(UiLanguage.German, awaitItem().selectedLanguage)
            val state2 = viewModel.state.value

            assertNotSame(state1, state2)
            assertEquals(UiLanguage.English, state1.selectedLanguage)
            assertEquals(UiLanguage.German, state2.selectedLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
