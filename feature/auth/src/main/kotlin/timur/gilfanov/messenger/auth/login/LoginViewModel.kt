package timur.gilfanov.messenger.auth.login

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
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginWithCredentials: LoginWithCredentialsUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    private val _effects = Channel<LoginSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun updateEmail(email: String) {
        _state.update { it.copy(email = email, emailError = null) }
    }

    fun updatePassword(password: String) {
        _state.update { it.copy(password = password, passwordError = null) }
    }

    fun submitLogin() {
        if (_state.value.isLoading) return
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
                onFailure = { error -> _state.update { it.withLoginError(error) } },
            )
        }
    }

    fun submitGoogleSignIn(idToken: String) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            val result = loginWithGoogle(GoogleIdToken(idToken))
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(LoginSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    _state.update { it.copy(generalError = error.toGeneralError()) }
                },
            )
        }
    }
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

    is LoginUseCaseError.InvalidEmail -> copy(generalError = LoginGeneralError.Unknown)

    is LoginUseCaseError.LocalOperationFailed -> copy(
        generalError = LoginGeneralError.ServiceUnavailable,
    )

    is LoginUseCaseError.RemoteOperationFailed -> copy(
        generalError = when (error.error) {
            RemoteError.Failed.NetworkNotAvailable -> LoginGeneralError.NetworkUnavailable
            else -> LoginGeneralError.ServiceUnavailable
        },
    )
}

private fun GoogleLoginUseCaseError.toGeneralError(): LoginGeneralError = when (this) {
    GoogleLoginUseCaseError.InvalidToken -> LoginGeneralError.InvalidToken

    GoogleLoginUseCaseError.AccountNotFound -> LoginGeneralError.AccountNotFound

    GoogleLoginUseCaseError.AccountSuspended -> LoginGeneralError.AccountSuspended

    is GoogleLoginUseCaseError.LocalOperationFailed -> LoginGeneralError.ServiceUnavailable

    is GoogleLoginUseCaseError.RemoteOperationFailed -> when (error) {
        RemoteError.Failed.NetworkNotAvailable -> LoginGeneralError.NetworkUnavailable
        else -> LoginGeneralError.ServiceUnavailable
    }
}
