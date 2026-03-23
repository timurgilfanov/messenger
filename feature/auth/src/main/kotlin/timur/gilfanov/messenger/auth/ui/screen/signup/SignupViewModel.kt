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
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val signupWithGoogle: SignupWithGoogleUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SignupUiState(
            name = savedStateHandle[KEY_NAME] ?: "",
        ),
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<SignupSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var lastIdToken: String? = null

    companion object {
        private const val KEY_NAME = "name"
    }

    fun updateName(name: String) {
        savedStateHandle[KEY_NAME] = name
        _state.update { it.copy(name = name, nameError = null) }
    }

    fun submitSignupWithGoogle(idToken: String) {
        if (_state.value.isLoading) return
        lastIdToken = idToken
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null, nameError = null) }
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
                            _state.update { it.copy(nameError = error.reason) }

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

    fun retryLastAction() {
        _state.update { it.copy(blockingError = null) }
        val idToken = lastIdToken ?: return
        submitSignupWithGoogle(idToken)
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
