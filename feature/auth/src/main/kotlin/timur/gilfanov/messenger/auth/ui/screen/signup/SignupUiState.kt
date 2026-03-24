package timur.gilfanov.messenger.auth.ui.screen.signup

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

@Immutable
data class SignupUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val nameError: ProfileNameValidationError? = null,
    val generalError: SignupGeneralError? = null,
    val blockingError: SignupBlockingError? = null,
)

@Immutable
sealed interface SignupGeneralError {
    data object InvalidToken : SignupGeneralError
    data object AccountAlreadyExists : SignupGeneralError
}

@Immutable
sealed interface SignupSnackbarMessage {
    data object NetworkUnavailable : SignupSnackbarMessage
    data object ServiceUnavailable : SignupSnackbarMessage
    data object GoogleSignUpFailed : SignupSnackbarMessage
    data object StorageTemporarilyUnavailable : SignupSnackbarMessage
    data class TooManyAttempts(val remaining: Duration) : SignupSnackbarMessage
}

@Immutable
sealed interface SignupBlockingError {
    data object StorageFull : SignupBlockingError
    data object StorageCorrupted : SignupBlockingError
    data object StorageReadOnly : SignupBlockingError
    data object StorageAccessDenied : SignupBlockingError
}
