package timur.gilfanov.messenger.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun LoginScreen(
    onNavigateToChatList: () -> Unit,
    onNavigateToSignup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnNavigateToChatList by rememberUpdatedState(onNavigateToChatList)
    val currentOnNavigateToSignup by rememberUpdatedState(onNavigateToSignup)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.onEach { effect ->
                when (effect) {
                    LoginSideEffects.NavigateToChatList -> currentOnNavigateToChatList()
                    LoginSideEffects.NavigateToSignup -> currentOnNavigateToSignup()
                }
            }.collect()
        }
    }

    val handleGoogleSignIn: () -> Unit = {
        scope.launch {
            val credentialManager = CredentialManager.create(context)
            val request = GetCredentialRequest(
                listOf(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.google_server_client_id))
                        .build(),
                ),
            )
            try {
                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is GoogleIdTokenCredential) {
                    viewModel.submitGoogleSignIn(credential.idToken)
                }
            } catch (_: NoCredentialException) {
            } catch (_: GetCredentialCancellationException) {
            } catch (_: GetCredentialException) {
                viewModel.onGoogleSignInFailed()
            }
        }
    }

    LoginScreenContent(
        state = state,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onSubmitLogin = viewModel::submitLogin,
        onGoogleSignInClick = handleGoogleSignIn,
        onNavigateToSignup = { currentOnNavigateToSignup() },
        modifier = modifier,
    )
}

@Suppress("LongParameterList") // Compose property drilling is preferred over wrapper objects
@Composable
fun LoginScreenContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onNavigateToSignup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("login_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmailField(state, onEmailChange)

            PasswordField(state, onPasswordChange)

            state.generalError?.let {
                Text(text = it.toDisplayString())
            }

            Button(
                onClick = onSubmitLogin,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_sign_in_button))
            }

            Button(
                onClick = onGoogleSignInClick,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_sign_in_with_google_button))
            }

            TextButton(onClick = onNavigateToSignup) {
                Text(stringResource(R.string.login_create_account_button))
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PasswordField(state: LoginUiState, onPasswordChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        isError = state.passwordError != null,
        supportingText = state.passwordError?.let { error ->
            {
                Text(error.toDisplayString())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmailField(state: LoginUiState, onEmailChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.login_email_label)) },
        isError = state.emailError != null,
        supportingText = state.emailError?.let { error ->
            {
                Text(error.toDisplayString())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CredentialsValidationError.toDisplayString(): String = when (this) {
    CredentialsValidationError.BlankEmail -> stringResource(R.string.login_error_blank_email)

    CredentialsValidationError.InvalidEmailFormat -> stringResource(
        R.string.login_error_invalid_email_format,
    )

    CredentialsValidationError.NoAtInEmail -> stringResource(R.string.login_error_no_at_in_email)

    is CredentialsValidationError.EmailTooLong -> stringResource(
        R.string.login_error_email_too_long,
        maxLength,
    )

    CredentialsValidationError.NoDomainAtEmail -> stringResource(
        R.string.login_error_no_domain_at_email,
    )

    is CredentialsValidationError.ForbiddenCharacterInEmail ->
        stringResource(R.string.login_error_forbidden_character_in_email, character)

    is CredentialsValidationError.PasswordTooShort ->
        stringResource(R.string.login_error_password_too_short, minLength)

    is CredentialsValidationError.PasswordTooLong ->
        stringResource(R.string.login_error_password_too_long, maxLength)

    is CredentialsValidationError.ForbiddenCharacterInPassword ->
        stringResource(R.string.login_error_forbidden_character_in_password, character)

    is CredentialsValidationError.PasswordMustContainNumbers ->
        stringResource(R.string.login_error_password_must_contain_numbers, minNumbers)

    is CredentialsValidationError.PasswordMustContainAlphabet ->
        stringResource(R.string.login_error_password_must_contain_alphabet, minAlphabet)
}

@Composable
private fun LoginGeneralError.toDisplayString(): String = stringResource(
    when (this) {
        LoginGeneralError.InvalidCredentials -> R.string.login_error_invalid_credentials
        LoginGeneralError.EmailNotVerified -> R.string.login_error_email_not_verified
        LoginGeneralError.AccountSuspended -> R.string.login_error_account_suspended
        LoginGeneralError.AccountNotFound -> R.string.login_error_account_not_found
        LoginGeneralError.InvalidToken -> R.string.login_error_invalid_token
        LoginGeneralError.NetworkUnavailable -> R.string.login_error_network_unavailable
        LoginGeneralError.ServiceUnavailable -> R.string.login_error_service_unavailable
        LoginGeneralError.Unknown -> R.string.login_error_unknown
        LoginGeneralError.GoogleSignInFailed -> R.string.login_error_google_sign_in_failed
    },
)

@Preview
@Composable
private fun LoginScreenContentLightPreview() {
    Content(darkTheme = false)
}

@Preview
@Composable
private fun LoginScreenContentDarkPreview() {
    Content(darkTheme = true)
}

@Preview
@Composable
private fun LoginScreenContentWithErrorsPreview() {
    Content(
        darkTheme = false,
        state = LoginUiState(
            email = "bad-email",
            emailError = CredentialsValidationError.InvalidEmailFormat,
            passwordError = CredentialsValidationError.PasswordTooShort(
                CredentialsValidatorImpl.MIN_PASSWORD_LENGTH,
            ),
        ),
    )
}

@Preview
@Composable
private fun LoginScreenContentWithFilledFieldsPreview() {
    Content(
        darkTheme = false,
        state = LoginUiState(
            email = "user@example.com",
            password = "Password1",
        ),
    )
}

@Preview
@Composable
private fun LoginScreenContentLoadingPreview() {
    Content(darkTheme = false, state = LoginUiState(isLoading = true))
}

@Composable
private fun Content(darkTheme: Boolean, state: LoginUiState = LoginUiState()) {
    MessengerTheme(darkTheme = darkTheme) {
        LoginScreenContent(
            state = state,
            onEmailChange = {},
            onPasswordChange = {},
            onSubmitLogin = {},
            onGoogleSignInClick = {},
            onNavigateToSignup = {},
        )
    }
}
