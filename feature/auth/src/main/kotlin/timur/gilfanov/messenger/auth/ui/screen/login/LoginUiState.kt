package timur.gilfanov.messenger.auth.ui.screen.login

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isCredentialsValid: Boolean = false,
    val emailError: CredentialsValidationError? = null,
    val passwordError: CredentialsValidationError? = null,
    val generalError: LoginGeneralError? = null,
    val blockingError: LoginBlockingError? = null,
) {
    val isSubmitEnabled: Boolean get() = !isLoading && isCredentialsValid
}

@Immutable
sealed interface LoginGeneralError {
    data object InvalidCredentials : LoginGeneralError
    data object EmailNotVerified : LoginGeneralError
    data object AccountSuspended : LoginGeneralError
    data object InvalidEmail : LoginGeneralError
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
