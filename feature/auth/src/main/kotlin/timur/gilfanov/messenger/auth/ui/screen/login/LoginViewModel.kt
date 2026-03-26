package timur.gilfanov.messenger.auth.ui.screen.login

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
import timur.gilfanov.messenger.auth.domain.usecase.GoogleLoginUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.LoginUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginWithCredentials: LoginWithCredentialsUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val credentialsValidator: CredentialsValidator,
) : ViewModel() {

    private val _state = MutableStateFlow(
        run {
            val email: String = savedStateHandle[KEY_EMAIL] ?: ""
            LoginUiState(
                email = email,
                isCredentialsValid = false,
            )
        },
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
        val currentPassword = _state.value.password
        val isCredentialsValid = credentialsValidator.validate(
            Credentials(Email(email), Password(currentPassword)),
        ) is ResultWithError.Success
        _state.update {
            it.copy(email = email, emailError = null, isCredentialsValid = isCredentialsValid)
        }
    }

    fun updatePassword(password: String) {
        val currentEmail = _state.value.email
        val isCredentialsValid = credentialsValidator.validate(
            Credentials(Email(currentEmail), Password(password)),
        ) is ResultWithError.Success
        _state.update {
            it.copy(
                password = password,
                passwordError = null,
                isCredentialsValid = isCredentialsValid,
            )
        }
    }

    companion object {
        private const val KEY_EMAIL = "email"
    }

    fun submitLogin() {
        if (_state.value.isLoading) {
            return
        }
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
                    when (error) {
                        is LoginUseCaseError.ValidationFailed -> handleValidationError(error)

                        LoginUseCaseError.InvalidCredentials ->
                            _state.update {
                                it.copy(
                                    generalError = LoginGeneralError.InvalidCredentials,
                                    isCredentialsValid = false,
                                )
                            }

                        LoginUseCaseError.EmailNotVerified ->
                            _state.update {
                                it.copy(generalError = LoginGeneralError.EmailNotVerified)
                            }

                        LoginUseCaseError.AccountSuspended ->
                            _state.update {
                                it.copy(generalError = LoginGeneralError.AccountSuspended)
                            }

                        is LoginUseCaseError.InvalidEmail ->
                            _state.update {
                                it.copy(
                                    generalError = LoginGeneralError.InvalidEmail,
                                    isCredentialsValid = false,
                                )
                            }

                        is LoginUseCaseError.RemoteOperationFailed ->
                            _effects.send(
                                LoginSideEffects.ShowSnackbar(error.error.toSnackbarMessage()),
                            )

                        is LoginUseCaseError.LocalOperationFailed ->
                            handleLocalStorageError(error.error)
                    }
                },
            )
        }
    }

    fun submitGoogleSignIn(idToken: String) {
        if (_state.value.isLoading) {
            return
        }
        lastLoginAction = LastLoginAction.Google(idToken)
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            val result = loginWithGoogle(GoogleIdToken(idToken))
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(LoginSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    when (error) {
                        GoogleLoginUseCaseError.InvalidToken ->
                            _state.update { it.copy(generalError = LoginGeneralError.InvalidToken) }

                        GoogleLoginUseCaseError.AccountNotFound ->
                            _state.update {
                                it.copy(generalError = LoginGeneralError.AccountNotFound)
                            }

                        GoogleLoginUseCaseError.AccountSuspended ->
                            _state.update {
                                it.copy(generalError = LoginGeneralError.AccountSuspended)
                            }

                        is GoogleLoginUseCaseError.RemoteOperationFailed ->
                            _effects.send(
                                LoginSideEffects.ShowSnackbar(error.error.toSnackbarMessage()),
                            )

                        is GoogleLoginUseCaseError.LocalOperationFailed ->
                            handleLocalStorageError(error.error)
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

    private fun handleValidationError(error: LoginUseCaseError.ValidationFailed) {
        _state.update { state ->
            when (val ve = error.error) {
                is CredentialsValidationError.BlankEmail,
                is CredentialsValidationError.NoAtInEmail,
                is CredentialsValidationError.NoDomainAtEmail,
                is CredentialsValidationError.EmailTooLong,
                is CredentialsValidationError.InvalidEmailFormat,
                is CredentialsValidationError.ForbiddenCharacterInEmail,
                -> state.copy(emailError = ve, isCredentialsValid = false)

                is CredentialsValidationError.PasswordTooShort,
                is CredentialsValidationError.PasswordTooLong,
                is CredentialsValidationError.ForbiddenCharacterInPassword,
                is CredentialsValidationError.PasswordMustContainNumbers,
                is CredentialsValidationError.PasswordMustContainAlphabet,
                -> state.copy(passwordError = ve, isCredentialsValid = false)
            }
        }
    }

    private suspend fun handleLocalStorageError(error: LocalStorageError) {
        when (error) {
            LocalStorageError.StorageFull ->
                _state.update { it.copy(blockingError = LoginBlockingError.StorageFull) }

            LocalStorageError.Corrupted ->
                _state.update { it.copy(blockingError = LoginBlockingError.StorageCorrupted) }

            LocalStorageError.ReadOnly ->
                _state.update { it.copy(blockingError = LoginBlockingError.StorageReadOnly) }

            LocalStorageError.AccessDenied ->
                _state.update { it.copy(blockingError = LoginBlockingError.StorageAccessDenied) }

            LocalStorageError.TemporarilyUnavailable ->
                _effects.send(
                    LoginSideEffects.ShowSnackbar(
                        LoginSnackbarMessage.StorageTemporarilyUnavailable,
                    ),
                )

            is LocalStorageError.UnknownError ->
                _effects.send(
                    LoginSideEffects.ShowSnackbar(
                        LoginSnackbarMessage.StorageTemporarilyUnavailable,
                    ),
                )
        }
    }
}

private fun UnauthRemoteError.toSnackbarMessage(): LoginSnackbarMessage = when (this) {
    RemoteError.Failed.NetworkNotAvailable -> LoginSnackbarMessage.NetworkUnavailable

    is RemoteError.Failed.Cooldown -> LoginSnackbarMessage.TooManyAttempts(remaining)

    RemoteError.Failed.ServiceDown,
    is RemoteError.Failed.UnknownServiceError,
    RemoteError.UnknownStatus.ServiceTimeout,
    -> LoginSnackbarMessage.ServiceUnavailable
}
