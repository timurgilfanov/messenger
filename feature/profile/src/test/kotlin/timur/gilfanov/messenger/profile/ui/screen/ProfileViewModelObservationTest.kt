package timur.gilfanov.messenger.profile.ui.screen

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ProfileViewModelObservationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        flow: kotlinx.coroutines.flow.Flow<ResultWithError<Profile, ObserveProfileError>>,
    ) = ProfileViewModel(
        observeProfile = ObserveProfileUseCaseStub(flow),
        logger = NoOpLogger(),
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `initial state is Loading`() {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Success(Profile("Timur", null))),
        )

        assertIs<ProfileUiState.Loading>(viewModel.state.value)
    }

    @Test
    fun `transitions to Ready on successful observation`() = runTest {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Success(Profile("Timur", null))),
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertEquals(
                ProfileUiState.Ready(ProfileUi("Timur", null)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `state updates on subsequent profile changes`() = runTest {
        val profileFlow = MutableStateFlow<ResultWithError<Profile, ObserveProfileError>>(
            ResultWithError.Success(Profile("Timur", null)),
        )
        val viewModel = createViewModel(profileFlow)

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertEquals(
                ProfileUiState.Ready(ProfileUi("Timur", null)),
                awaitItem(),
            )

            profileFlow.update {
                ResultWithError.Success(Profile("Alex", null))
            }

            assertEquals(
                ProfileUiState.Ready(ProfileUi("Alex", null)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `Unauthorized error does not post side effect`() = runTest {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized)),
        )

        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `state remains Loading on Unauthorized error`() = runTest {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized)),
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
        }
    }
}
