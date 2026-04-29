package timur.gilfanov.messenger.auth.ui.screen.login

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isCredentialsValid: Boolean = false,
    val emailError: LoginEmailError? = null,
    val passwordError: PasswordValidationError? = null,
    val generalError: LoginGeneralError? = null,
    val blockingError: LoginBlockingError? = null,
) {
    val isSubmitEnabled: Boolean
        get() = !isLoading && isCredentialsValid && emailError == null && passwordError == null
}

@Immutable
sealed interface LoginGeneralError {
    data object InvalidCredentials : LoginGeneralError
    data object EmailNotVerified : LoginGeneralError
    data object AccountSuspended : LoginGeneralError
    data object AccountNotFound : LoginGeneralError
    data object InvalidToken : LoginGeneralError
}

@Immutable
sealed interface LoginSnackbarMessage {
    data object NetworkUnavailable : LoginSnackbarMessage
    data object ServiceUnavailable : LoginSnackbarMessage
    data object GoogleSignInFailed : LoginSnackbarMessage
    data object StorageTemporarilyUnavailable : LoginSnackbarMessage
    data class TooManyAttempts(val remaining: Duration) : LoginSnackbarMessage
}

@Immutable
sealed interface LoginBlockingError {
    data object StorageFull : LoginBlockingError
    data object StorageCorrupted : LoginBlockingError
    data object StorageReadOnly : LoginBlockingError
    data object StorageAccessDenied : LoginBlockingError
}
