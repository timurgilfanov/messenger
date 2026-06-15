package timur.gilfanov.messenger.profile.ui.screen

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
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

    @Test
    fun `resets to Loading and clears SavedStateHandle when Unauthorized follows Ready state`() =
        runTest {
            val profileFlow = MutableStateFlow<ResultWithError<Profile, ObserveProfileError>>(
                ResultWithError.Success(Profile("Timur", null)),
            )
            val savedStateHandle = SavedStateHandle()
            val viewModel = ProfileViewModel(
                observeProfile = ObserveProfileUseCaseStub(profileFlow),
                logger = NoOpLogger(),
                savedStateHandle = savedStateHandle,
            )

            viewModel.state.test {
                assertIs<ProfileUiState.Loading>(awaitItem())
                assertIs<ProfileUiState.Ready>(awaitItem())

                profileFlow.update {
                    ResultWithError.Failure(ObserveProfileError.Unauthorized)
                }

                assertIs<ProfileUiState.Loading>(awaitItem())
                assertNull(savedStateHandle.get<ProfileUi>("profile"))
            }
        }

    @Test
    fun `local profile failure posts no side effect when no cached data`() = runTest {
        val error = ObserveProfileError.LocalOperationFailed(LocalStorageError.AccessDenied)
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(error)),
        )

        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `local profile failure transitions to Error state when no cached data`() = runTest {
        val error = ObserveProfileError.LocalOperationFailed(LocalStorageError.AccessDenied)
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(error)),
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
        }
    }

    @Test
    fun `remote profile failure transitions to Error state when no cached data`() = runTest {
        val error = ObserveProfileError.RemoteOperationFailed(
            RemoteError.Failed.NetworkNotAvailable,
        )
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(error)),
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
        }
    }

    @Test
    fun `InsufficientPermissions transitions to Error state when no cached data`() = runTest {
        val error = ObserveProfileError.RemoteOperationFailed(
            RemoteError.InsufficientPermissions,
        )
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(error)),
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
        }
    }

    @Test
    fun `InsufficientPermissions posts no side effect when no cached data`() = runTest {
        val error = ObserveProfileError.RemoteOperationFailed(
            RemoteError.InsufficientPermissions,
        )
        val viewModel = createViewModel(
            flowOf(ResultWithError.Failure(error)),
        )

        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `profile failure keeps Ready state when cached data exists`() = runTest {
        val profileFlow = MutableStateFlow<ResultWithError<Profile, ObserveProfileError>>(
            ResultWithError.Success(Profile("Timur", null)),
        )
        val viewModel = createViewModel(profileFlow)

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Ready>(awaitItem())

            profileFlow.update {
                ResultWithError.Failure(
                    ObserveProfileError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                )
            }

            expectNoEvents()
        }
    }

    @Test
    fun `profile failure posts side effect when cached data exists`() = runTest {
        val profileFlow = MutableStateFlow<ResultWithError<Profile, ObserveProfileError>>(
            ResultWithError.Success(Profile("Timur", null)),
        )
        val viewModel = createViewModel(profileFlow)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.effects.test {
            profileFlow.update {
                ResultWithError.Failure(
                    ObserveProfileError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                )
            }
            advanceUntilIdle()
            assertIs<ProfileSideEffects.Feedback.ObserveFailed>(awaitItem())
        }
    }

    @Test
    fun `error state is persisted to SavedStateHandle on first-load remote failure`() = runTest {
        val error = ObserveProfileError.RemoteOperationFailed(
            RemoteError.Failed.NetworkNotAvailable,
        )
        val savedStateHandle = SavedStateHandle()
        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(flowOf(ResultWithError.Failure(error))),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
            assertEquals(true, savedStateHandle.get<Boolean>("error_state"))
        }
    }

    @Test
    fun `error state is persisted to SavedStateHandle on first-load local failure`() = runTest {
        val error = ObserveProfileError.LocalOperationFailed(LocalStorageError.AccessDenied)
        val savedStateHandle = SavedStateHandle()
        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(flowOf(ResultWithError.Failure(error))),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
            assertEquals(true, savedStateHandle.get<Boolean>("error_state"))
        }
    }

    @Test
    fun `initial state is Error when SavedStateHandle contains error flag`() {
        val savedStateHandle = SavedStateHandle(mapOf("error_state" to true))
        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(kotlinx.coroutines.flow.emptyFlow()),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        assertIs<ProfileUiState.Error>(viewModel.state.value)
    }

    @Test
    fun `error flag is cleared from SavedStateHandle on successful observation`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("error_state" to true))
        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(
                flowOf(ResultWithError.Success(Profile("Timur", null))),
            ),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Error>(awaitItem())
            assertIs<ProfileUiState.Ready>(awaitItem())
            assertNull(savedStateHandle.get<Boolean>("error_state"))
        }
    }

    @Test
    fun `error flag is cleared from SavedStateHandle on Unauthorized`() = runTest {
        val profileFlow = MutableStateFlow<ResultWithError<Profile, ObserveProfileError>>(
            ResultWithError.Failure(
                ObserveProfileError.RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
            ),
        )
        val savedStateHandle = SavedStateHandle()
        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(profileFlow),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Error>(awaitItem())
            assertEquals(true, savedStateHandle.get<Boolean>("error_state"))

            profileFlow.update {
                ResultWithError.Failure(ObserveProfileError.Unauthorized)
            }

            assertIs<ProfileUiState.Loading>(awaitItem())
            assertNull(savedStateHandle.get<Boolean>("error_state"))
        }
    }
}
