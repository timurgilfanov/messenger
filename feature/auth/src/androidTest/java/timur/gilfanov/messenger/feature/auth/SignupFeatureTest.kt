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
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.ui.GoogleSignInClient
import timur.gilfanov.messenger.auth.ui.GoogleSignInClientStub
import timur.gilfanov.messenger.auth.ui.GoogleSignInResult
import timur.gilfanov.messenger.auth.ui.SignupScreenTestActivity
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.auth.validation.ProfileNameValidator
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(AuthModule::class, AuthViewModelModule::class, AuthDataModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class SignupFeatureTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
        private const val TEST_NAME = "Alice Smith"
        private const val TEST_EMAIL = "alice@example.com"
        private const val TEST_PASSWORD = "Password1"
        private const val TEST_GOOGLE_ID_TOKEN = "google-id-token"
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<SignupScreenTestActivity>()

    @Inject
    lateinit var authRepository: AuthRepository

    @Module
    @InstallIn(SingletonComponent::class)
    object SignupTestModule {
        // Shared across tests because Hilt modules must be objects and tests need to mutate the
        // stub (e.g. shouldSuspend = true) before the Hilt component is built — which happens
        // when the activity launches, before @Before runs. tearDown calls reset() to prevent
        // state from leaking into the next test.
        val googleSignInClient = GoogleSignInClientStub()
        val signupWithCredentialsUseCase = SignupWithCredentialsUseCaseStub()

        @Provides
        @Singleton
        fun provideAuthRepository(): AuthRepository =
            AuthRepositoryFake(initialAuthState = Unauthenticated)

        @Provides
        @Singleton
        fun provideProfileNameValidator(): ProfileNameValidator = ProfileNameValidatorImpl()

        @Provides
        @Singleton
        fun provideCredentialsValidator(): CredentialsValidator = CredentialsValidatorImpl()

        @Provides
        @Singleton
        fun provideSignupWithGoogleUseCase(
            nameValidator: ProfileNameValidator,
            repository: AuthRepository,
            logger: Logger,
        ): SignupWithGoogleUseCase = SignupWithGoogleUseCaseImpl(nameValidator, repository, logger)

        @Provides
        @Singleton
        fun provideSignupWithCredentialsUseCase(): SignupWithCredentialsUseCase =
            signupWithCredentialsUseCase

        @Provides
        @Singleton
        fun provideLogger(): Logger = NoOpLogger()

        @Provides
        @Singleton
        fun provideLoginWithCredentialsUseCase(): LoginWithCredentialsUseCase =
            LoginWithCredentialsUseCase { error("Not used in signup tests") }

        @Provides
        @Singleton
        fun provideLoginWithGoogleUseCase(): LoginWithGoogleUseCase =
            LoginWithGoogleUseCase { error("Not used in signup tests") }

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
        SignupTestModule.googleSignInClient.reset()
        SignupTestModule.signupWithCredentialsUseCase.reset()
    }

    @Test
    fun signupScreen_displaysFormElements() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("signup_screen"),
            timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
        )
        composeTestRule.onNodeWithTag("signup_name_field").assertExists()
        composeTestRule.onNodeWithTag("signup_google_sign_up_button").assertExists()
    }

    @Test
    fun signupScreen_displaysCredentialsFormElements() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("signup_screen"),
            timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
        )
        composeTestRule.onNodeWithTag("signup_email_field").assertExists()
        composeTestRule.onNodeWithTag("signup_password_field").assertExists()
        composeTestRule.onNodeWithTag("signup_credentials_sign_up_button").assertExists()
    }

    @Test
    fun signupScreen_handlesRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()
            onNodeWithTag("signup_name_field").assertExists()
            onNodeWithTag("signup_google_sign_up_button").assertExists()
        }
    }

    @Test
    fun signupScreen_preservesNameOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_name_field").performTextInput(TEST_NAME)

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            val nameLabel = activity.getString(R.string.signup_name_label)
            onNodeWithTag("signup_name_field").assertTextEquals(nameLabel, TEST_NAME)
        }
    }

    @Test
    fun signupScreen_preservesEmailOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_email_field").performTextInput(TEST_EMAIL)

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            val emailLabel = activity.getString(R.string.signup_email_label)
            onNodeWithTag("signup_email_field").assertTextEquals(emailLabel, TEST_EMAIL)
        }
    }

    @Test
    fun signupScreen_googleSignUpCancelled_showsNoError() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_google_sign_up_button").performClick()
            waitForIdle()
            onNodeWithTag("signup_general_error").assertDoesNotExist()
        }
    }

    @Test
    fun signupScreen_googleSignUpInvalidToken_showsError() {
        SignupTestModule.googleSignInClient.result =
            GoogleSignInResult.Success(TEST_GOOGLE_ID_TOKEN)
        (authRepository as AuthRepositoryFake).enqueueSignupWithGoogleResult(
            ResultWithError.Failure(GoogleSignupRepositoryError.InvalidToken),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_name_field").performTextInput(TEST_NAME)
            onNodeWithTag("signup_google_sign_up_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("signup_general_error"))
            onNodeWithTag("signup_general_error")
                .assertTextEquals(activity.getString(R.string.signup_error_invalid_token))
        }
    }

    @Test
    fun signupScreen_rapidTapGoogleSignUp_buttonBecomesDisabled() {
        SignupTestModule.googleSignInClient.shouldSuspend = true
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_google_sign_up_button").performClick()
            waitForIdle()
            onNodeWithTag("signup_google_sign_up_button").assertIsNotEnabled()
        }
    }

    @Test
    fun signupScreen_credentialsSignupSuccess_navigatesToChatList() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_name_field").performTextInput(TEST_NAME)
            onNodeWithTag("signup_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("signup_password_field").performTextInput(TEST_PASSWORD)
            onNodeWithTag("signup_credentials_sign_up_button").performClick()
            waitUntilDoesNotExist(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
        }
    }

    @Test
    fun signupScreen_credentialsSignupClientEmailError_showsEmailFieldError() {
        SignupTestModule.signupWithCredentialsUseCase.result = ResultWithError.Failure(
            SignupWithCredentialsUseCaseError.ValidationFailed(
                CredentialsValidationError.BlankEmail,
            ),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_credentials_sign_up_button").performClick()
            waitUntil(timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS) {
                onAllNodes(hasTestTag("signup_email_error"), useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }
            onNodeWithTag("signup_email_error", useUnmergedTree = true)
                .assertTextEquals(activity.getString(R.string.login_error_blank_email))
        }
    }

    @Test
    fun signupScreen_credentialsSignup_buttonDisabledWhileLoading() {
        SignupTestModule.signupWithCredentialsUseCase.shouldSuspend = true
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_name_field").performTextInput(TEST_NAME)
            onNodeWithTag("signup_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("signup_password_field").performTextInput(TEST_PASSWORD)
            onNodeWithTag("signup_credentials_sign_up_button").performClick()
            waitForIdle()
            onNodeWithTag("signup_credentials_sign_up_button").assertIsNotEnabled()
        }
    }

    @Test
    fun signupScreen_localOperationFailed_storageFull_showsBlockingDialog() {
        SignupTestModule.googleSignInClient.result =
            GoogleSignInResult.Success(TEST_GOOGLE_ID_TOKEN)
        (authRepository as AuthRepositoryFake).enqueueSignupWithGoogleResult(
            ResultWithError.Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_name_field").performTextInput(TEST_NAME)
            onNodeWithTag("signup_google_sign_up_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("signup_blocking_error_dialog"))
            onNodeWithTag("signup_blocking_error_action_button")
                .assertTextEquals(
                    activity.getString(R.string.auth_action_open_storage_settings),
                )
        }
    }

    @Test
    fun signupScreen_googleSignUpFailed_showsError() {
        SignupTestModule.googleSignInClient.result = GoogleSignInResult.Failed
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("signup_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("signup_google_sign_up_button").performClick()
            waitUntilExactlyOneExists(
                hasText(activity.getString(R.string.auth_error_google_sign_in_failed)),
            )
        }
    }
}
