package timur.gilfanov.messenger.ui.screen.settings

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ProfileViewModelObservationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    private fun createViewModel(
        flow: kotlinx.coroutines.flow.Flow<ResultWithError<Profile, ObserveProfileError>>,
    ) = ProfileViewModel(
        observeProfile = ObserveProfileUseCaseStub(flow),
        logger = NoOpLogger(),
    )

    @Test
    fun `initial state is Loading`() {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Success(Profile(testUserId, "Timur", null))),
        )

        assertIs<ProfileUiState.Loading>(viewModel.state.value)
    }

    @Test
    fun `transitions to Ready on successful observation`() = runTest {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Success(Profile(testUserId, "Timur", null))),
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
            ResultWithError.Success(Profile(testUserId, "Timur", null)),
        )
        val viewModel = createViewModel(profileFlow)

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertEquals(
                ProfileUiState.Ready(ProfileUi("Timur", null)),
                awaitItem(),
            )

            profileFlow.update {
                ResultWithError.Success(Profile(testUserId, "Alex", null))
            }

            assertEquals(
                ProfileUiState.Ready(ProfileUi("Alex", null)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `sends Unauthorized effect on Unauthorized error`() = runTest {
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized)),
        )

        viewModel.effects.test {
            assertEquals(ProfileSideEffects.Unauthorized, awaitItem())
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
