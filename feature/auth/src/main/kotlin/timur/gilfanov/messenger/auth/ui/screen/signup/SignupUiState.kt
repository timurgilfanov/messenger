package timur.gilfanov.messenger.auth.ui.screen.signup

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import timur.gilfanov.messenger.auth.domain.usecase.EmailValidationUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.PasswordValidationUseCaseError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

@Immutable
data class SignupUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isNameValid: Boolean = false,
    val isCredentialsValid: Boolean = false,
    val isPasswordConfirmed: Boolean = false,
    val nameError: ProfileNameValidationError? = null,
    val emailError: EmailValidationUseCaseError? = null,
    val passwordError: PasswordValidationUseCaseError? = null,
    val generalError: SignupGeneralError? = null,
    val blockingError: SignupBlockingError? = null,
) {
    val isGoogleSubmitEnabled: Boolean get() = !isLoading && isNameValid
    val isCredentialsSubmitEnabled: Boolean
        get() = !isLoading && isNameValid && isCredentialsValid && isPasswordConfirmed
    val isPasswordMismatched: Boolean
        get() = !isPasswordConfirmed && password.trim().length == confirmPassword.trim().length &&
            password.isNotBlank()
}

@Immutable
sealed interface SignupGeneralError {
    data object InvalidToken : SignupGeneralError
    data object AccountAlreadyExists : SignupGeneralError
}

@Immutable
sealed interface SignupSnackbarMessage {
    data object NetworkUnavailable : SignupSnackbarMessage
    data object ServiceUnavailable : SignupSnackbarMessage
    data object GoogleSignInFailed : SignupSnackbarMessage
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
