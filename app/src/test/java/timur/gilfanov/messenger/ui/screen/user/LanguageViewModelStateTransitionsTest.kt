package timur.gilfanov.messenger.ui.screen.user

import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSettingsRepositoryWithFlow
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createTestSettings
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModelTestFixtures.createViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LanguageViewModelStateTransitionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Verifies that the `languages` list property maintains referential equality across
     * state changes, ensuring only the `selectedLanguage` is updated while the list itself
     * is reused.
     *
     * **Why this matters:**
     * - **Performance**: Jetpack Compose uses referential equality for recomposition.
     *   If `languages` never changes reference, UI elements depending on it won't recompose
     *   unnecessarily
     * - **Memory efficiency**: The immutable list is created once during initialization
     *   and shared across all state instances
     * - **Correctness**: Verifies proper state management where only changing data is updated
     *   while static data remains constant
     *
     * **What would be wrong:**
     * If each `state.copy(selectedLanguage = X)` created a new `languages` list,
     * it would cause unnecessary recompositions and memory allocations.
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
     * Verifies that each state update creates a new [LanguageUiState] instance,
     * ensuring state objects are never mutated in place and previous state instances
     * remain unchanged.
     *
     * **Why this matters:**
     * - **Correctness**: MVI pattern requires immutable state for predictable behavior
     *   and unidirectional data flow
     * - **Thread safety**: Immutable state can be safely shared across coroutines
     *   without synchronization
     * - **Time travel debugging**: Previous state instances must remain unchanged
     *   for debugging tools to work correctly
     * - **Compose integration**: Compose snapshot system relies on new object instances
     *   to detect state changes and trigger recomposition
     *
     * **What would be wrong:**
     * If state were mutated in place (`state.selectedLanguage = X`), it would break
     * MVI guarantees, cause race conditions, and prevent Compose from detecting changes.
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
