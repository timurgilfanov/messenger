package timur.gilfanov.messenger.auth.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.login.LoginSnackbarMessage.TooManyAttempts
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginWithCredentials: LoginWithCredentialsUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            email = savedStateHandle[KEY_EMAIL] ?: "",
            password = savedStateHandle[KEY_PASSWORD] ?: "",
        ),
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<LoginSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private sealed interface LastLoginAction {
        data object Credentials : LastLoginAction
        data class Google(val idToken: String) : LastLoginAction
    }

    private var lastLoginAction: LastLoginAction? = null

    fun updateEmail(email: String) {
        savedStateHandle[KEY_EMAIL] = email
        _state.update { it.copy(email = email, emailError = null) }
    }

    fun updatePassword(password: String) {
        savedStateHandle[KEY_PASSWORD] = password
        _state.update { it.copy(password = password, passwordError = null) }
    }

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }

    fun submitLogin() {
        if (_state.value.isLoading) return
        lastLoginAction = LastLoginAction.Credentials
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    emailError = null,
                    passwordError = null,
                    generalError = null,
                )
            }
            val currentState = _state.value
            val credentials =
                Credentials(Email(currentState.email), Password(currentState.password))
            val result = loginWithCredentials(credentials)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(LoginSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    val snackbar = error.toSnackbarMessage()
                    if (snackbar != null) {
                        _effects.send(LoginSideEffects.ShowSnackbar(snackbar))
                    } else {
                        _state.update { it.withLoginError(error) }
                    }
                },
            )
        }
    }

    fun onGoogleSignInFailed() {
        viewModelScope.launch {
            _effects.send(
                LoginSideEffects.ShowSnackbar(LoginSnackbarMessage.GoogleSignInFailed),
            )
        }
    }

    fun submitGoogleSignIn(idToken: String) {
        if (_state.value.isLoading) return
        lastLoginAction = LastLoginAction.Google(idToken)
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            val result = loginWithGoogle(GoogleIdToken(idToken))
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(LoginSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    val snackbar = error.toSnackbarMessage()
                    if (snackbar != null) {
                        _effects.send(LoginSideEffects.ShowSnackbar(snackbar))
                    } else {
                        _state.update { it.withGoogleLoginError(error) }
                    }
                },
            )
        }
    }

    fun retryLastAction() {
        _state.update { it.copy(blockingError = null) }
        when (val action = lastLoginAction) {
            is LastLoginAction.Credentials -> submitLogin()
            is LastLoginAction.Google -> submitGoogleSignIn(action.idToken)
            null -> Unit
        }
    }

    fun onOpenAppSettingsClick() {
        _state.update { it.copy(blockingError = null) }
        viewModelScope.launch { _effects.send(LoginSideEffects.OpenAppSettings) }
    }

    fun onOpenStorageSettingsClick() {
        _state.update { it.copy(blockingError = null) }
        viewModelScope.launch { _effects.send(LoginSideEffects.OpenStorageSettings) }
    }
}

private fun LoginUseCaseError.toSnackbarMessage(): LoginSnackbarMessage? = when (this) {
    is LoginUseCaseError.RemoteOperationFailed -> error.toSnackbarMessage()

    is LoginUseCaseError.LocalOperationFailed -> error.toSnackbarMessage()

    LoginUseCaseError.AccountSuspended,
    LoginUseCaseError.EmailNotVerified,
    LoginUseCaseError.InvalidCredentials,
    is LoginUseCaseError.InvalidEmail,
    is LoginUseCaseError.ValidationFailed,
    -> null
}

private fun GoogleLoginUseCaseError.toSnackbarMessage(): LoginSnackbarMessage? = when (this) {
    is GoogleLoginUseCaseError.RemoteOperationFailed -> error.toSnackbarMessage()

    is GoogleLoginUseCaseError.LocalOperationFailed -> error.toSnackbarMessage()

    GoogleLoginUseCaseError.AccountNotFound,
    GoogleLoginUseCaseError.AccountSuspended,
    GoogleLoginUseCaseError.InvalidToken,
    -> null
}

private fun UnauthRemoteError.toSnackbarMessage(): LoginSnackbarMessage = when (this) {
    RemoteError.Failed.NetworkNotAvailable -> LoginSnackbarMessage.NetworkUnavailable

    is RemoteError.Failed.Cooldown -> TooManyAttempts(remaining)

    RemoteError.Failed.ServiceDown,
    is RemoteError.Failed.UnknownServiceError,
    RemoteError.UnknownStatus.ServiceTimeout,
    -> LoginSnackbarMessage.ServiceUnavailable
}

private fun LocalStorageError.toSnackbarMessage(): LoginSnackbarMessage? = when (this) {
    LocalStorageError.TemporarilyUnavailable -> LoginSnackbarMessage.StorageTemporarilyUnavailable

    is LocalStorageError.UnknownError -> LoginSnackbarMessage.StorageTemporarilyUnavailable

    LocalStorageError.AccessDenied,
    LocalStorageError.Corrupted,
    LocalStorageError.ReadOnly,
    LocalStorageError.StorageFull,
    -> null
}

private fun LoginUiState.withLoginError(error: LoginUseCaseError): LoginUiState = when (error) {
    is LoginUseCaseError.ValidationFailed -> when (error.error) {
        is CredentialsValidationError.BlankEmail,
        is CredentialsValidationError.NoAtInEmail,
        is CredentialsValidationError.NoDomainAtEmail,
        is CredentialsValidationError.EmailTooLong,
        is CredentialsValidationError.InvalidEmailFormat,
        is CredentialsValidationError.ForbiddenCharacterInEmail,
        -> copy(emailError = error.error)

        is CredentialsValidationError.PasswordTooShort,
        is CredentialsValidationError.PasswordTooLong,
        is CredentialsValidationError.ForbiddenCharacterInPassword,
        is CredentialsValidationError.PasswordMustContainNumbers,
        is CredentialsValidationError.PasswordMustContainAlphabet,
        -> copy(passwordError = error.error)
    }

    LoginUseCaseError.InvalidCredentials -> copy(
        generalError = LoginGeneralError.InvalidCredentials,
    )

    LoginUseCaseError.EmailNotVerified -> copy(generalError = LoginGeneralError.EmailNotVerified)

    LoginUseCaseError.AccountSuspended -> copy(generalError = LoginGeneralError.AccountSuspended)

    is LoginUseCaseError.InvalidEmail -> copy(generalError = LoginGeneralError.InvalidEmail)

    is LoginUseCaseError.LocalOperationFailed -> copy(
        blockingError = error.error.toBlockingError(),
    )

    is LoginUseCaseError.RemoteOperationFailed -> this
}

private fun LoginUiState.withGoogleLoginError(error: GoogleLoginUseCaseError): LoginUiState =
    when (error) {
        GoogleLoginUseCaseError.InvalidToken -> copy(generalError = LoginGeneralError.InvalidToken)

        GoogleLoginUseCaseError.AccountNotFound ->
            copy(generalError = LoginGeneralError.AccountNotFound)

        GoogleLoginUseCaseError.AccountSuspended ->
            copy(generalError = LoginGeneralError.AccountSuspended)

        is GoogleLoginUseCaseError.RemoteOperationFailed -> this

        is GoogleLoginUseCaseError.LocalOperationFailed -> copy(
            blockingError = error.error.toBlockingError(),
        )
    }

private fun LocalStorageError.toBlockingError(): LoginBlockingError = when (this) {
    LocalStorageError.StorageFull -> LoginBlockingError.StorageFull

    LocalStorageError.Corrupted -> LoginBlockingError.StorageCorrupted

    LocalStorageError.ReadOnly -> LoginBlockingError.StorageReadOnly

    LocalStorageError.AccessDenied -> LoginBlockingError.StorageAccessDenied

    LocalStorageError.TemporarilyUnavailable,
    is LocalStorageError.UnknownError,
    -> error("Snackbar-eligible storage error reached toBlockingError: $this")
}
