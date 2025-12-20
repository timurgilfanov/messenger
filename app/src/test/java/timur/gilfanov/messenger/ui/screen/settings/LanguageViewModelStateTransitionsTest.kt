package timur.gilfanov.messenger.ui.screen.settings

import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentListOf
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
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelStateTransitionsTest {

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

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }
            val languages1 = viewModel.container.stateFlow.value.languages

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }
            val languages2 = viewModel.container.stateFlow.value.languages

            assertSame(languages1, languages2)
            assertEquals(
                persistentListOf(UiLanguage.English, UiLanguage.German),
                languages2,
            )

            job.cancelAndJoin()
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

        viewModel.test(this) {
            val job = runOnCreate()

            expectState {
                copy(selectedLanguage = UiLanguage.English)
            }
            val state1 = viewModel.container.stateFlow.value

            settingsFlow.update {
                ResultWithError.Success(createTestSettings(UiLanguage.German))
            }

            expectState {
                copy(selectedLanguage = UiLanguage.German)
            }
            val state2 = viewModel.container.stateFlow.value

            assertNotSame(state1, state2)
            assertEquals(UiLanguage.English, state1.selectedLanguage)
            assertEquals(UiLanguage.German, state2.selectedLanguage)

            job.cancelAndJoin()
        }
    }
}
