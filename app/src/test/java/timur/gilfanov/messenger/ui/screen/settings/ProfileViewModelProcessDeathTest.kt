package timur.gilfanov.messenger.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ProfileViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Test
    fun `restores profile from SavedStateHandle after process death`() = runTest {
        val uri = Uri.parse("https://example.com/avatar.jpg")
        val restoredSavedStateHandle = SavedStateHandle(
            mapOf("profile" to ProfileUi("Timur", uri)),
        )

        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(emptyFlow()),
            logger = NoOpLogger(),
            savedStateHandle = restoredSavedStateHandle,
        )

        viewModel.state.test {
            val state = awaitItem()
            assertIs<ProfileUiState.Ready>(state)
            assertEquals(ProfileUi("Timur", uri), state.profile)
        }
    }

    @Test
    fun `saves profile to SavedStateHandle when state becomes Ready`() = runTest {
        val savedStateHandle = SavedStateHandle()

        val viewModel = ProfileViewModel(
            observeProfile = ObserveProfileUseCaseStub(
                flowOf(
                    ResultWithError.Success(
                        Profile(testUserId, "Timur", "https://example.com/avatar.jpg"),
                    ),
                ),
            ),
            logger = NoOpLogger(),
            savedStateHandle = savedStateHandle,
        )

        viewModel.state.test {
            assertIs<ProfileUiState.Loading>(awaitItem())
            assertIs<ProfileUiState.Ready>(awaitItem())
        }

        assertEquals(
            ProfileUi("Timur", Uri.parse("https://example.com/avatar.jpg")),
            savedStateHandle.get<ProfileUi>("profile"),
        )
    }
}
