package timur.gilfanov.messenger.feature.auth

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.auth.di.AuthDataModule
import timur.gilfanov.messenger.auth.di.AuthModule
import timur.gilfanov.messenger.auth.di.AuthViewModelModule
import timur.gilfanov.messenger.auth.login.GoogleSignInClient
import timur.gilfanov.messenger.auth.login.GoogleSignInClientStub
import timur.gilfanov.messenger.auth.login.GoogleSignInResult
import timur.gilfanov.messenger.auth.login.LoginScreenTestActivity
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(AuthModule::class, AuthViewModelModule::class, AuthDataModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class LoginFeatureTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
        private const val TEST_EMAIL = "user@example.com"
        private const val TEST_PASSWORD = "Password1"
        private const val TEST_GOOGLE_ID_TOKEN = "google-id-token"
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<LoginScreenTestActivity>()

    @Inject
    lateinit var authRepository: AuthRepository

    @Module
    @InstallIn(SingletonComponent::class)
    object LoginTestModule {
        // Shared across tests because Hilt modules must be objects and tests need to mutate the
        // stub (e.g. shouldSuspend = true) before the Hilt component is built — which happens
        // when the activity launches, before @Before runs. tearDown calls reset() to prevent
        // state from leaking into the next test.
        val googleSignInClient = GoogleSignInClientStub()

        @Provides
        @Singleton
        fun provideAuthRepository(): AuthRepository =
            AuthRepositoryFake(initialAuthState = Unauthenticated)

        @Provides
        @Singleton
        fun provideCredentialsValidator(): CredentialsValidator = CredentialsValidatorImpl()

        @Provides
        @Singleton
        fun provideLoginWithCredentialsUseCase(
            validator: CredentialsValidator,
            repository: AuthRepository,
            logger: Logger,
        ): LoginWithCredentialsUseCase =
            LoginWithCredentialsUseCaseImpl(validator, repository, logger)

        @Provides
        @Singleton
        fun provideLoginWithGoogleUseCase(
            repository: AuthRepository,
            logger: Logger,
        ): LoginWithGoogleUseCase = LoginWithGoogleUseCaseImpl(repository, logger)

        @Provides
        @Singleton
        fun provideLogger(): Logger = NoOpLogger()

        @Provides
        @Singleton
        fun provideGoogleSignInClient(): GoogleSignInClient = googleSignInClient
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        LoginTestModule.googleSignInClient.reset()
    }

    @Test
    fun loginScreen_displaysFormElements() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("login_screen"),
            timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
        )
        composeTestRule.onNodeWithTag("login_email_field").assertExists()
        composeTestRule.onNodeWithTag("login_password_field").assertExists()
        composeTestRule.onNodeWithTag("login_sign_in_button").assertExists()
        composeTestRule.onNodeWithTag("login_google_sign_in_button").assertExists()
    }

    @Test
    fun loginScreen_handlesRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()
            onNodeWithTag("login_email_field").assertExists()
            onNodeWithTag("login_sign_in_button").assertExists()
        }
    }

    @Test
    fun loginScreen_handlesMultipleActivityRecreation() = runTest {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            repeat(100) {
                withContext(Dispatchers.Main) {
                    activity.recreate()
                }
                onNodeWithTag("login_screen").assertExists()
            }
        }
    }

    @Test
    fun loginScreen_preservesInputOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("login_password_field").performTextInput(TEST_PASSWORD)

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            val emailLabel = activity.getString(R.string.login_email_label)
            val passwordLabel = activity.getString(R.string.login_password_label)
            onNodeWithTag("login_email_field").assertTextEquals(emailLabel, TEST_EMAIL)
            onNodeWithTag("login_password_field")
                .assertTextEquals(passwordLabel, passwordLabel, "•".repeat(TEST_PASSWORD.length))
        }
    }

    @Test
    fun loginScreen_preservesValidationErrorOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_sign_in_button").performClick()
            waitUntil(timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS) {
                onAllNodes(hasTestTag("login_email_error"), useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            val errorBlankEmail = activity.getString(R.string.login_error_blank_email)
            onNodeWithTag("login_email_error", useUnmergedTree = true)
                .assertTextEquals(errorBlankEmail)
        }
    }

    @Test
    fun loginScreen_showsInvalidCredentialsError() {
        (authRepository as AuthRepositoryFake).enqueueLoginWithCredentialsResult(
            ResultWithError.Failure(LoginRepositoryError.InvalidCredentials),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("login_password_field").performTextInput(TEST_PASSWORD)
            onNodeWithTag("login_sign_in_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("login_general_error"))
            onNodeWithTag("login_general_error")
                .assertTextEquals(activity.getString(R.string.login_error_invalid_credentials))
        }
    }

    @Test
    fun loginScreen_googleSignInCancelled_showsNoError() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_google_sign_in_button").performClick()
            waitForIdle()
            onNodeWithTag("login_general_error").assertDoesNotExist()
        }
    }

    @Test
    fun loginScreen_googleSignInFailed_showsError() {
        LoginTestModule.googleSignInClient.result = GoogleSignInResult.Failed
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_google_sign_in_button").performClick()
            waitUntilExactlyOneExists(
                hasText(activity.getString(R.string.login_error_google_sign_in_failed)),
            )
        }
    }

    @Test
    fun loginScreen_googleSignInAccountNotFound_showsError() {
        LoginTestModule.googleSignInClient.result = GoogleSignInResult.Success(TEST_GOOGLE_ID_TOKEN)
        (authRepository as AuthRepositoryFake).enqueueLoginWithGoogleResult(
            ResultWithError.Failure(GoogleLoginRepositoryError.AccountNotFound),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_google_sign_in_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("login_general_error"))
            onNodeWithTag("login_general_error")
                .assertTextEquals(activity.getString(R.string.login_error_account_not_found))
        }
    }

    @Test
    fun loginScreen_googleSignInAccountSuspended_showsError() {
        LoginTestModule.googleSignInClient.result = GoogleSignInResult.Success(TEST_GOOGLE_ID_TOKEN)
        (authRepository as AuthRepositoryFake).enqueueLoginWithGoogleResult(
            ResultWithError.Failure(GoogleLoginRepositoryError.AccountSuspended),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_google_sign_in_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("login_general_error"))
            onNodeWithTag("login_general_error")
                .assertTextEquals(activity.getString(R.string.login_error_account_suspended))
        }
    }

    @Test
    fun loginScreen_rapidTapGoogleSignIn_buttonBecomesDisabled() {
        LoginTestModule.googleSignInClient.shouldSuspend = true
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_google_sign_in_button").performClick()
            waitForIdle()
            onNodeWithTag("login_google_sign_in_button").assertIsNotEnabled()
        }
    }

    @Test
    fun loginScreen_localOperationFailed_storageFull_showsBlockingDialog() {
        (authRepository as AuthRepositoryFake).enqueueLoginWithCredentialsResult(
            ResultWithError.Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("login_password_field").performTextInput(TEST_PASSWORD)
            onNodeWithTag("login_sign_in_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("login_blocking_error_dialog"))
            onNodeWithTag("login_blocking_error_action_button")
                .assertTextEquals(activity.getString(R.string.login_action_open_storage_settings))
        }
    }
}
