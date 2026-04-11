@file:Suppress("TooManyFunctions")

package timur.gilfanov.messenger.auth.ui.screen.signup

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.auth.ui.GoogleSignInClient
import timur.gilfanov.messenger.auth.ui.GoogleSignInResult
import timur.gilfanov.messenger.auth.ui.utils.openAppSettings
import timur.gilfanov.messenger.auth.ui.utils.openStorageSettings
import timur.gilfanov.messenger.auth.ui.utils.toDisplayString
import timur.gilfanov.messenger.auth.ui.utils.tooManyAttemptsDisplayString
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailUnknownError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun SignupScreen(
    onNavigateToChatList: () -> Unit,
    onNavigateBack: () -> Unit,
    googleSignInClient: GoogleSignInClient,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSigningUpWithGoogle by remember { mutableStateOf(false) }

    SignupEffectHandler(
        effects = viewModel.effects,
        snackbarHostState = snackbarHostState,
        onNavigateToChatList = onNavigateToChatList,
        onRetry = viewModel::retryLastAction,
    )

    val onGoogleSignUpClick: () -> Unit = {
        if (!isSigningUpWithGoogle) {
            isSigningUpWithGoogle = true
            scope.launch {
                when (val result = googleSignInClient.signIn(context)) {
                    is GoogleSignInResult.Success -> {
                        viewModel.submitSignupWithGoogle(result.idToken)
                        isSigningUpWithGoogle = false
                    }

                    GoogleSignInResult.Cancelled -> {
                        isSigningUpWithGoogle = false
                    }

                    GoogleSignInResult.Failed -> {
                        isSigningUpWithGoogle = false
                        val msg = SignupSnackbarMessage.GoogleSignInFailed.toDisplayString(context)
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
            }
        }
    }

    SignupScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNameChange = viewModel::updateName,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onCredentialsSignUpClick = viewModel::submitSignupWithCredentials,
        onGoogleSignUpClick = onGoogleSignUpClick,
        isSigningUpWithGoogle = isSigningUpWithGoogle,
        onNavigateBack = onNavigateBack,
        onOpenAppSettings = viewModel::onOpenAppSettingsClick,
        onOpenStorageSettings = viewModel::onOpenStorageSettingsClick,
        modifier = modifier,
    )
}

@Composable
private fun SignupEffectHandler(
    effects: Flow<SignupSideEffects>,
    snackbarHostState: SnackbarHostState,
    onNavigateToChatList: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnNavigateToChatList by rememberUpdatedState(onNavigateToChatList)
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.onEach { effect ->
                when (effect) {
                    SignupSideEffects.NavigateToChatList -> currentOnNavigateToChatList()

                    SignupSideEffects.OpenAppSettings -> openAppSettings(context)

                    SignupSideEffects.OpenStorageSettings -> openStorageSettings(context)

                    is SignupSideEffects.ShowSnackbar -> scope.launch {
                        val message = effect.message
                        val actionLabel =
                            if (message is SignupSnackbarMessage.StorageTemporarilyUnavailable) {
                                context.getString(R.string.auth_action_retry)
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

@Suppress("LongParameterList")
@Composable
fun SignupScreenContent(
    state: SignupUiState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onCredentialsSignUpClick: () -> Unit,
    onGoogleSignUpClick: () -> Unit,
    isSigningUpWithGoogle: Boolean,
    onNavigateBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("signup_screen"),
    ) {
        SignupForm(
            state = state,
            onNameChange = onNameChange,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onCredentialsSignUpClick = onCredentialsSignUpClick,
            onGoogleSignUpClick = onGoogleSignUpClick,
            isSigningUpWithGoogle = isSigningUpWithGoogle,
            onNavigateBack = onNavigateBack,
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
        SignupBlockingErrorDialog(
            error = error,
            onOpenAppSettings = onOpenAppSettings,
            onOpenStorageSettings = onOpenStorageSettings,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun SignupForm(
    state: SignupUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onCredentialsSignUpClick: () -> Unit,
    onGoogleSignUpClick: () -> Unit,
    isSigningUpWithGoogle: Boolean,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignupCredentialsFields(
            state = state,
            onNameChange = onNameChange,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
        )

        Spacer(modifier = Modifier.height(4.dp))

        state.generalError?.let {
            Text(
                text = it.toDisplayString(),
                modifier = Modifier.testTag("signup_general_error"),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onCredentialsSignUpClick,
            enabled = state.isCredentialsSubmitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_credentials_sign_up_button"),
        ) {
            Text(stringResource(R.string.signup_sign_up_button))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onGoogleSignUpClick,
            enabled = state.isGoogleSubmitEnabled && !isSigningUpWithGoogle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_google_sign_up_button"),
        ) {
            Text(stringResource(R.string.signup_sign_up_with_google_button))
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.signup_already_have_account_button))
        }
    }
}

@Composable
private fun SignupCredentialsFields(
    state: SignupUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
) {
    val areFieldsEnabled = !state.isLoading

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NameField(
            name = state.name,
            nameError = state.nameError?.toDisplayString(),
            onNameChange = onNameChange,
            enabled = areFieldsEnabled,
        )

        EmailField(
            email = state.email,
            emailError = state.emailError?.toDisplayString(),
            onEmailChange = onEmailChange,
            enabled = areFieldsEnabled,
        )

        PasswordField(
            password = state.password,
            passwordError = state.passwordError?.toDisplayString(),
            onPasswordChange = onPasswordChange,
            enabled = areFieldsEnabled,
        )

        ConfirmPasswordField(
            confirmPassword = state.confirmPassword,
            isPasswordMismatch = state.isPasswordMismatched,
            onConfirmPasswordChange = onConfirmPasswordChange,
            enabled = areFieldsEnabled,
        )
    }
}

@Composable
private fun NameField(
    name: String,
    nameError: String?,
    onNameChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.signup_name_label)) },
        singleLine = true,
        enabled = enabled,
        isError = nameError != null,
        supportingText = nameError?.let { error ->
            {
                Text(
                    text = error,
                    modifier = Modifier.testTag("signup_name_error"),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signup_name_field"),
    )
}

@Composable
private fun EmailField(
    email: String,
    emailError: String?,
    onEmailChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.signup_email_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        singleLine = true,
        enabled = enabled,
        isError = emailError != null,
        supportingText = emailError?.let { error ->
            {
                Text(
                    text = error,
                    modifier = Modifier.testTag("signup_email_error"),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signup_email_field"),
    )
}

@Composable
private fun PasswordField(
    password: String,
    passwordError: String?,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.signup_password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        singleLine = true,
        enabled = enabled,
        isError = passwordError != null,
        supportingText = passwordError?.let { error ->
            {
                Text(
                    text = error,
                    modifier = Modifier.testTag("signup_password_error"),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signup_password_field"),
    )
}

@Composable
private fun ConfirmPasswordField(
    confirmPassword: String,
    isPasswordMismatch: Boolean,
    onConfirmPasswordChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text(stringResource(R.string.signup_confirm_password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        enabled = enabled,
        isError = isPasswordMismatch,
        supportingText = if (isPasswordMismatch) {
            {
                Text(
                    text = stringResource(R.string.signup_error_password_mismatch),
                    modifier = Modifier.testTag("signup_confirm_password_error"),
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signup_confirm_password_field"),
    )
}

@Composable
private fun ProfileNameValidationError.toDisplayString(): String = when (this) {
    is ProfileNameValidationError.LengthOutOfBounds ->
        stringResource(R.string.signup_error_name_length_out_of_bounds, min, max)

    is ProfileNameValidationError.ForbiddenCharacter ->
        stringResource(R.string.signup_error_name_forbidden_character)

    is ProfileNameValidationError.PlatformPolicyViolation.Pornography,
    is ProfileNameValidationError.PlatformPolicyViolation.Violence,
    is ProfileNameValidationError.PlatformPolicyViolation.IllegalSubstance,
    -> stringResource(R.string.signup_error_name_policy_violation)

    is ProfileNameValidationError.UnknownRuleViolation ->
        stringResource(R.string.signup_error_name_unknown_rule)
}

@Composable
private fun SignupEmailError.toDisplayString(): String = when (this) {
    EmailValidationError.BlankEmail -> stringResource(R.string.auth_error_blank_email)

    EmailValidationError.InvalidEmailFormat ->
        stringResource(R.string.auth_error_invalid_email_format)

    EmailValidationError.NoAtInEmail -> stringResource(R.string.auth_error_no_at_in_email)

    is EmailValidationError.EmailTooLong ->
        stringResource(R.string.auth_error_email_too_long, maxLength)

    EmailValidationError.NoDomainAtEmail -> stringResource(R.string.auth_error_no_domain_at_email)

    SignupEmailError.EmailTaken -> stringResource(R.string.signup_error_email_taken)

    is EmailUnknownError -> stringResource(R.string.signup_error_invalid_email_server)
}

@Composable
private fun SignupGeneralError.toDisplayString(): String = when (this) {
    SignupGeneralError.InvalidToken -> stringResource(R.string.signup_error_invalid_token)
    SignupGeneralError.AccountAlreadyExists ->
        stringResource(R.string.signup_error_account_already_exists)
}

private fun SignupSnackbarMessage.toDisplayString(context: Context): String = when (this) {
    SignupSnackbarMessage.NetworkUnavailable ->
        context.getString(R.string.auth_error_network_unavailable)

    SignupSnackbarMessage.ServiceUnavailable ->
        context.getString(R.string.auth_error_service_unavailable)

    SignupSnackbarMessage.GoogleSignInFailed ->
        context.getString(R.string.auth_error_google_sign_in_failed)

    SignupSnackbarMessage.StorageTemporarilyUnavailable ->
        context.getString(R.string.auth_error_storage_temporarily_unavailable)

    is SignupSnackbarMessage.TooManyAttempts -> tooManyAttemptsDisplayString(context, remaining)
}

@Preview
@Composable
private fun SignupScreenContentLightPreview() {
    Content(darkTheme = false)
}

@Preview
@Composable
private fun SignupScreenContentDarkPreview() {
    Content(darkTheme = true)
}

private const val NAME = "John Doe"

private const val EMAIL = "user@example.com"

private const val PASSWORD = "Password1"

private const val PASSWORD_2 = "Password2"

@Preview
@Composable
private fun SignupScreenContentWithFilledFieldsPreview() {
    Content(
        darkTheme = false,
        state = SignupUiState(
            name = NAME,
            email = EMAIL,
            password = PASSWORD,
            confirmPassword = PASSWORD,
            isNameValid = true,
            isCredentialsValid = true,
            isPasswordConfirmed = true,
        ),
    )
}

@Preview
@Composable
private fun SignupScreenContentLoadingPreview() {
    Content(darkTheme = false, state = SignupUiState(isLoading = true))
}

@Preview
@Composable
private fun SignupScreenContentGeneralErrorPreview() {
    Content(
        darkTheme = false,
        state = SignupUiState(
            name = NAME,
            isNameValid = true,
            generalError = SignupGeneralError.AccountAlreadyExists,
        ),
    )
}

@Preview
@Composable
private fun SignupScreenContentPasswordMismatchedPreview() {
    Content(
        darkTheme = false,
        state = SignupUiState(
            name = NAME,
            email = EMAIL,
            password = PASSWORD,
            confirmPassword = PASSWORD_2,
            isNameValid = true,
            isCredentialsValid = true,
            isPasswordConfirmed = false,
        ),
    )
}

@Composable
private fun Content(darkTheme: Boolean, state: SignupUiState = SignupUiState()) {
    MessengerTheme(darkTheme = darkTheme) {
        SignupScreenContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onCredentialsSignUpClick = {},
            onGoogleSignUpClick = {},
            isSigningUpWithGoogle = false,
            onNavigateBack = {},
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}
