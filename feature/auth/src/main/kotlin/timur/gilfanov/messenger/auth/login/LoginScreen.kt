@file:Suppress("TooManyFunctions")

package timur.gilfanov.messenger.auth.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
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
    googleSignInClient: GoogleSignInClient,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigateToSignup by rememberUpdatedState(onNavigateToSignup)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSigningInWithGoogle by remember { mutableStateOf(false) }

    LoginEffectHandler(
        effects = viewModel.effects,
        snackbarHostState = snackbarHostState,
        onNavigateToChatList = onNavigateToChatList,
        onNavigateToSignup = onNavigateToSignup,
        onRetry = viewModel::retryLastAction,
    )

    val onGoogleSignInClick: () -> Unit = {
        if (!isSigningInWithGoogle) {
            isSigningInWithGoogle = true
            scope.launch {
                when (val result = googleSignInClient.signIn(context)) {
                    is GoogleSignInResult.Success -> viewModel.submitGoogleSignIn(result.idToken)

                    GoogleSignInResult.Cancelled -> Unit

                    GoogleSignInResult.Failed -> snackbarHostState.showSnackbar(
                        message = LoginSnackbarMessage.GoogleSignInFailed.toDisplayString(context),
                        duration = SnackbarDuration.Long,
                    )
                }
                isSigningInWithGoogle = false
            }
        }
    }
    LoginScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onSubmitLogin = viewModel::submitLogin,
        onGoogleSignInClick = onGoogleSignInClick,
        isSigningInWithGoogle = isSigningInWithGoogle,
        onNavigateToSignup = currentOnNavigateToSignup,
        onOpenAppSettings = viewModel::onOpenAppSettingsClick,
        onOpenStorageSettings = viewModel::onOpenStorageSettingsClick,
        modifier = modifier,
    )
}

@Composable
private fun LoginEffectHandler(
    effects: Flow<LoginSideEffects>,
    snackbarHostState: SnackbarHostState,
    onNavigateToChatList: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnNavigateToChatList by rememberUpdatedState(onNavigateToChatList)
    val currentOnNavigateToSignup by rememberUpdatedState(onNavigateToSignup)
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.onEach { effect ->
                when (effect) {
                    LoginSideEffects.NavigateToChatList -> currentOnNavigateToChatList()

                    LoginSideEffects.NavigateToSignup -> currentOnNavigateToSignup()

                    LoginSideEffects.OpenAppSettings -> openAppSettings(context)

                    LoginSideEffects.OpenStorageSettings -> openStorageSettings(context)

                    is LoginSideEffects.ShowSnackbar -> scope.launch {
                        val message = effect.message
                        val actionLabel =
                            if (message is LoginSnackbarMessage.StorageTemporarilyUnavailable) {
                                context.getString(R.string.login_action_retry)
                            } else {
                                null
                            }
                        val result = snackbarHostState.showSnackbar(
                            message = message.toDisplayString(context),
                            actionLabel = actionLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) currentOnRetry()
                    }
                }
            }.collect()
        }
    }
}

@Suppress("LongParameterList") // Compose property drilling is preferred over wrapper objects
@Composable
fun LoginScreenContent(
    state: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    isSigningInWithGoogle: Boolean,
    onNavigateToSignup: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("login_screen"),
    ) {
        LoginForm(
            state = state,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onSubmitLogin = onSubmitLogin,
            onGoogleSignInClick = onGoogleSignInClick,
            isSigningInWithGoogle = isSigningInWithGoogle,
            onNavigateToSignup = onNavigateToSignup,
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }

    state.blockingError?.let { error ->
        BlockingErrorDialog(
            error = error,
            onOpenAppSettings = onOpenAppSettings,
            onOpenStorageSettings = onOpenStorageSettings,
        )
    }
}

@Suppress("LongParameterList") // mirrors LoginScreenContent drilling
@Composable
private fun LoginForm(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    isSigningInWithGoogle: Boolean,
    onNavigateToSignup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmailField(state.email, state.emailError?.toDisplayString(), onEmailChange)

        Spacer(modifier = Modifier.height(4.dp))

        PasswordField(state.password, state.passwordError?.toDisplayString(), onPasswordChange)

        Spacer(modifier = Modifier.height(4.dp))

        state.generalError?.let {
            Text(
                text = it.toDisplayString(),
                modifier = Modifier.testTag("login_general_error"),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onSubmitLogin,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_sign_in_button"),
        ) {
            Text(stringResource(R.string.login_sign_in_button))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onGoogleSignInClick,
            enabled = !state.isLoading && !isSigningInWithGoogle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_google_sign_in_button"),
        ) {
            Text(stringResource(R.string.login_sign_in_with_google_button))
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onNavigateToSignup) {
            Text(stringResource(R.string.login_create_account_button))
        }
    }
}

@Composable
private fun PasswordField(
    password: String,
    passwordError: String?,
    onPasswordChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        isError = passwordError != null,
        supportingText = passwordError?.let { error ->
            {
                Text(
                    text = error,
                    modifier = Modifier.testTag("login_password_error"),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password_field"),
    )
}

@Composable
private fun EmailField(email: String, emailError: String?, onEmailChange: (String) -> Unit) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.login_email_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        isError = emailError != null,
        supportingText = emailError?.let { error ->
            {
                Text(
                    text = error,
                    modifier = Modifier.testTag("login_email_error"),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_email_field"),
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
private fun LoginGeneralError.toDisplayString(): String = when (this) {
    LoginGeneralError.InvalidCredentials -> stringResource(R.string.login_error_invalid_credentials)
    LoginGeneralError.EmailNotVerified -> stringResource(R.string.login_error_email_not_verified)
    LoginGeneralError.AccountSuspended -> stringResource(R.string.login_error_account_suspended)
    LoginGeneralError.AccountNotFound -> stringResource(R.string.login_error_account_not_found)
    LoginGeneralError.InvalidToken -> stringResource(R.string.login_error_invalid_token)
    LoginGeneralError.InvalidEmail -> stringResource(R.string.login_error_invalid_email)
}

private fun LoginSnackbarMessage.toDisplayString(context: Context): String = when (this) {
    LoginSnackbarMessage.NetworkUnavailable ->
        context.getString(R.string.login_error_network_unavailable)

    LoginSnackbarMessage.ServiceUnavailable ->
        context.getString(R.string.login_error_service_unavailable)

    LoginSnackbarMessage.Unknown ->
        context.getString(R.string.login_error_unknown)

    LoginSnackbarMessage.GoogleSignInFailed ->
        context.getString(R.string.login_error_google_sign_in_failed)

    LoginSnackbarMessage.StorageTemporarilyUnavailable ->
        context.getString(R.string.login_error_storage_temporarily_unavailable)

    is LoginSnackbarMessage.TooManyAttempts -> tooManyAttemptsDisplayString(context, remaining)
}

private fun tooManyAttemptsDisplayString(context: Context, remaining: Duration): String {
    val totalSeconds = remaining.inWholeSeconds
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val formatted = when {
        minutes > 0 && seconds > 0 ->
            "${
                context.resources.getQuantityString(
                    R.plurals.login_cooldown_minutes,
                    minutes.toInt(),
                    minutes,
                )
            } " +
                context.resources.getQuantityString(
                    R.plurals.login_cooldown_seconds,
                    seconds.toInt(),
                    seconds,
                )

        minutes > 0 ->
            context.resources.getQuantityString(
                R.plurals.login_cooldown_minutes,
                minutes.toInt(),
                minutes,
            )

        else ->
            context.resources.getQuantityString(
                R.plurals.login_cooldown_seconds,
                seconds.toInt(),
                seconds,
            )
    }
    return context.getString(R.string.login_error_too_many_attempts, formatted)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun openStorageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    if (intent.resolveActivity(context.packageManager) == null) {
        intent.action = Settings.ACTION_SETTINGS
    }
    context.startActivity(intent)
}

private const val SECONDS_PER_MINUTE = 60

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

@Preview
@Composable
private fun LoginScreenContentGeneralErrorPreview() {
    Content(
        darkTheme = false,
        state = LoginUiState(
            email = "user@example.com",
            password = "Password1",
            generalError = LoginGeneralError.InvalidCredentials,
        ),
    )
}

@Preview
@Composable
private fun LoginScreenContentBlockingErrorPreview() {
    Content(
        darkTheme = false,
        state = LoginUiState(blockingError = LoginBlockingError.StorageAccessDenied),
    )
}

@Preview(locale = "")
@Composable
private fun LoginScreenContentTooManyAttemptsEnPreview() {
    val context = LocalContext.current
    ContentWithSnackbar(
        message = LoginSnackbarMessage.TooManyAttempts(0.seconds)
            .toDisplayString(context),
    )
}

@Preview(locale = "de")
@Composable
private fun LoginScreenContentTooManyAttemptsDePreview() {
    val context = LocalContext.current
    ContentWithSnackbar(
        message = LoginSnackbarMessage.TooManyAttempts(3.minutes + 45.seconds)
            .toDisplayString(context),
    )
}

@Composable
private fun ContentWithSnackbar(message: String) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
    }
    MessengerTheme(darkTheme = false) {
        LoginScreenContent(
            state = LoginUiState(),
            snackbarHostState = snackbarHostState,
            onEmailChange = {},
            onPasswordChange = {},
            onSubmitLogin = {},
            onGoogleSignInClick = {},
            isSigningInWithGoogle = false,
            onNavigateToSignup = {},
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}

@Composable
private fun Content(darkTheme: Boolean, state: LoginUiState = LoginUiState()) {
    MessengerTheme(darkTheme = darkTheme) {
        LoginScreenContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onEmailChange = {},
            onPasswordChange = {},
            onSubmitLogin = {},
            onGoogleSignInClick = {},
            isSigningInWithGoogle = false,
            onNavigateToSignup = {},
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}
