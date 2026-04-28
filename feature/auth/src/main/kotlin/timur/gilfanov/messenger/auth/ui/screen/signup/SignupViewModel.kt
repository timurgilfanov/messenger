package timur.gilfanov.messenger.auth.ui.screen.signup

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
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseError
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidator
import timur.gilfanov.messenger.auth.domain.validation.ProfileNameValidator
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
@Suppress("TooManyFunctions") // a lot of actions required in this ViewModel
class SignupViewModel @Inject constructor(
    private val signupWithGoogle: SignupWithGoogleUseCase,
    private val signupWithCredentials: SignupWithCredentialsUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val profileNameValidator: ProfileNameValidator,
    private val credentialsValidator: CredentialsValidator,
) : ViewModel() {

    private val _state = MutableStateFlow(
        run {
            val name: String = savedStateHandle[KEY_NAME] ?: ""
            val email: String = savedStateHandle[KEY_EMAIL] ?: ""
            val nameValidation =
                if (name.isNotEmpty()) profileNameValidator.validate(name) else null
            val emailValidation =
                if (email.isNotEmpty()) credentialsValidator.validate(Email(email)) else null
            val credentialsValidation = credentialsValidator.validate(
                Credentials(Email(email), Password("")),
            )
            SignupUiState(
                name = name,
                email = email,
                isNameValid = nameValidation is ResultWithError.Success,
                nameError = (nameValidation as? ResultWithError.Failure)?.error,
                isCredentialsValid = credentialsValidation is ResultWithError.Success,
                emailError = (emailValidation as? ResultWithError.Failure)?.error,
            )
        },
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<SignupSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private sealed interface LastSignupAction {
        data object Credentials : LastSignupAction
        data class Google(val idToken: String) : LastSignupAction
    }

    private var lastSignupAction: LastSignupAction? = null

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
    }

    fun updateName(name: String) {
        savedStateHandle[KEY_NAME] = name
        val nameValidationResult = profileNameValidator.validate(name)
        val isNameValid = nameValidationResult is ResultWithError.Success
        val nameError = (nameValidationResult as? ResultWithError.Failure)?.error
        _state.update { it.copy(name = name, nameError = nameError, isNameValid = isNameValid) }
    }

    fun updateEmail(email: String) {
        savedStateHandle[KEY_EMAIL] = email
        val currentPassword = _state.value.password
        val credentialsResult = credentialsValidator.validate(
            Credentials(Email(email), Password(currentPassword)),
        )
        val isCredentialsValid = credentialsResult is ResultWithError.Success
        val credError = (credentialsResult as? ResultWithError.Failure)?.error
        val emailError = (
            credentialsValidator.validate(
                Email(email),
            ) as? ResultWithError.Failure
            )?.error
        // Password error depends on email via `PasswordError.PasswordEqualToEmail`
        val passwordError = when {
            currentPassword.isEmpty() -> _state.value.passwordError
            credError is CredentialsValidationError.Password -> credError.reason
            credentialsResult is ResultWithError.Success -> null
            else -> (
                credentialsValidator.validate(
                    Password(currentPassword),
                ) as? ResultWithError.Failure
                )?.error
        }
        _state.update {
            it.copy(
                email = email,
                emailError = emailError,
                passwordError = passwordError,
                isCredentialsValid = isCredentialsValid,
            )
        }
    }

    fun updatePassword(password: String) {
        val currentEmail = _state.value.email
        val credentialsResult = credentialsValidator.validate(
            Credentials(Email(currentEmail), Password(password)),
        )
        val isCredentialsValid = credentialsResult is ResultWithError.Success
        val credError = (credentialsResult as? ResultWithError.Failure)?.error
        // Email check have a priority in credentials validation in `CredentialsValidatorImpl`
        val passwordError = when (credError) {
            is CredentialsValidationError.Password -> credError.reason
            else -> (
                credentialsValidator.validate(
                    Password(password),
                ) as? ResultWithError.Failure
                )?.error
        }
        val currentConfirmPassword = _state.value.confirmPassword
        val isPasswordConfirmed = password == currentConfirmPassword
        _state.update {
            it.copy(
                password = password,
                passwordError = passwordError,
                isCredentialsValid = isCredentialsValid,
                isPasswordConfirmed = isPasswordConfirmed,
            )
        }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        val isPasswordConfirmed = confirmPassword == _state.value.password
        _state.update {
            it.copy(
                confirmPassword = confirmPassword,
                isPasswordConfirmed = isPasswordConfirmed,
            )
        }
    }

    fun submitSignupWithGoogle(idToken: String) {
        if (_state.value.isLoading) return
        lastSignupAction = LastSignupAction.Google(idToken)
        _state.update { it.copy(isLoading = true, generalError = null, nameError = null) }
        viewModelScope.launch {
            val currentName = _state.value.name
            val result = signupWithGoogle(GoogleIdToken(idToken), currentName)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(SignupSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    when (error) {
                        SignupWithGoogleUseCaseError.InvalidToken ->
                            _state.update {
                                it.copy(generalError = SignupGeneralError.InvalidToken)
                            }

                        SignupWithGoogleUseCaseError.AccountAlreadyExists ->
                            _state.update {
                                it.copy(generalError = SignupGeneralError.AccountAlreadyExists)
                            }

                        is SignupWithGoogleUseCaseError.InvalidName ->
                            _state.update { it.copy(nameError = error.reason, isNameValid = false) }

                        is SignupWithGoogleUseCaseError.RemoteOperationFailed ->
                            _effects.send(
                                SignupSideEffects.ShowSnackbar(error.error.toSnackbarMessage()),
                            )

                        is SignupWithGoogleUseCaseError.LocalOperationFailed ->
                            handleLocalStorageError(error.error)
                    }
                },
            )
        }
    }

    fun submitSignupWithCredentials() {
        if (_state.value.isLoading) return
        lastSignupAction = LastSignupAction.Credentials
        _state.update {
            it.copy(
                isLoading = true,
                emailError = null,
                passwordError = null,
                generalError = null,
                nameError = null,
            )
        }
        viewModelScope.launch {
            val currentState = _state.value
            val credentials =
                Credentials(Email(currentState.email), Password(currentState.password))
            val result = signupWithCredentials(credentials, currentState.name)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(SignupSideEffects.NavigateToChatList) },
                onFailure = { error ->
                    when (error) {
                        is SignupWithCredentialsUseCaseError.InvalidEmail ->
                            _state.update {
                                it.copy(emailError = error.reason, isCredentialsValid = false)
                            }

                        is SignupWithCredentialsUseCaseError.InvalidPassword ->
                            _state.update {
                                it.copy(passwordError = error.reason, isCredentialsValid = false)
                            }

                        is SignupWithCredentialsUseCaseError.InvalidName ->
                            _state.update { it.copy(nameError = error.reason, isNameValid = false) }

                        is SignupWithCredentialsUseCaseError.RemoteOperationFailed ->
                            _effects.send(
                                SignupSideEffects.ShowSnackbar(error.error.toSnackbarMessage()),
                            )

                        is SignupWithCredentialsUseCaseError.LocalOperationFailed ->
                            handleLocalStorageError(error.error)
                    }
                },
            )
        }
    }

    fun retryLastAction() {
        _state.update { it.copy(blockingError = null) }
        when (val action = lastSignupAction) {
            is LastSignupAction.Credentials -> submitSignupWithCredentials()
            is LastSignupAction.Google -> submitSignupWithGoogle(action.idToken)
            null -> Unit
        }
    }

    fun onOpenAppSettingsClick() {
        _state.update { it.copy(blockingError = null) }
        viewModelScope.launch { _effects.send(SignupSideEffects.OpenAppSettings) }
    }

    fun onOpenStorageSettingsClick() {
        _state.update { it.copy(blockingError = null) }
        viewModelScope.launch { _effects.send(SignupSideEffects.OpenStorageSettings) }
    }

    private suspend fun handleLocalStorageError(error: LocalStorageError) {
        when (error) {
            LocalStorageError.StorageFull ->
                _state.update { it.copy(blockingError = SignupBlockingError.StorageFull) }

            LocalStorageError.Corrupted ->
                _state.update { it.copy(blockingError = SignupBlockingError.StorageCorrupted) }

            LocalStorageError.ReadOnly ->
                _state.update { it.copy(blockingError = SignupBlockingError.StorageReadOnly) }

            LocalStorageError.AccessDenied ->
                _state.update { it.copy(blockingError = SignupBlockingError.StorageAccessDenied) }

            LocalStorageError.TemporarilyUnavailable ->
                _effects.send(
                    SignupSideEffects.ShowSnackbar(
                        SignupSnackbarMessage.StorageTemporarilyUnavailable,
                    ),
                )

            is LocalStorageError.UnknownError ->
                _effects.send(
                    SignupSideEffects.ShowSnackbar(
                        SignupSnackbarMessage.StorageTemporarilyUnavailable,
                    ),
                )
        }
    }
}

private fun UnauthRemoteError.toSnackbarMessage(): SignupSnackbarMessage = when (this) {
    RemoteError.Failed.NetworkNotAvailable -> SignupSnackbarMessage.NetworkUnavailable

    is RemoteError.Failed.Cooldown -> SignupSnackbarMessage.TooManyAttempts(remaining)

    RemoteError.Failed.ServiceDown,
    is RemoteError.Failed.UnknownServiceError,
    RemoteError.UnknownStatus.ServiceTimeout,
    -> SignupSnackbarMessage.ServiceUnavailable
}
