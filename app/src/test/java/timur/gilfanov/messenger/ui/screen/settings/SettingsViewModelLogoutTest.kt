package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.domain.usecase.LogoutError
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCaseImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SettingsViewModelLogoutTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsFlow = MutableStateFlow<ResultWithError<Settings, ObserveSettingsError>>(
        ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)),
    )

    private fun createViewModel(authRepository: AuthRepositoryFake): SettingsViewModel =
        SettingsViewModel(
            observeSettings = ObserveSettingsUseCaseStub(settingsFlow),
            logoutUseCase = LogoutUseCaseImpl(
                authRepository,
                NoOpLogger(),
            ),
            logger = NoOpLogger(),
            savedStateHandle = SavedStateHandle(),
        )

    @Test
    fun `logout success emits no effect`() = runTest {
        val authRepository = AuthRepositoryFake().apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
        }
        val viewModel = createViewModel(authRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.logout()
            expectNoEvents()
        }
    }

    @Test
    fun `logout local error sends LogoutFailed effect`() = runTest {
        val localError = LocalStorageError.UnknownError(Exception("disk error"))
        val authRepository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.LocalOperationFailed(localError))
        }
        val viewModel = createViewModel(authRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.logout()
            val effect = awaitItem()
            assertIs<SettingsSideEffects.LogoutFailed>(effect)
            assertIs<LogoutError.LocalOperationFailed>(effect.error)
        }
    }

    @Test
    fun `logout remote error sends LogoutFailed effect`() = runTest {
        val remoteError = RemoteError.Failed.NetworkNotAvailable
        val authRepository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.RemoteOperationFailed(remoteError))
        }
        val viewModel = createViewModel(authRepository)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.logout()
            val effect = awaitItem()
            assertIs<SettingsSideEffects.LogoutFailed>(effect)
            assertIs<LogoutError.RemoteOperationFailed>(effect.error)
        }
    }
}
