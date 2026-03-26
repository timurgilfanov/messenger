package timur.gilfanov.messenger.auth.ui.screen.signup

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

@Immutable
data class SignupUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val nameError: ProfileNameValidationError? = null,
    val emailError: CredentialsValidationError? = null,
    val passwordError: CredentialsValidationError? = null,
    val generalError: SignupGeneralError? = null,
    val blockingError: SignupBlockingError? = null,
)

@Immutable
sealed interface SignupGeneralError {
    data object InvalidToken : SignupGeneralError
    data object AccountAlreadyExists : SignupGeneralError
    data class InvalidEmail(val reason: EmailValidationError) : SignupGeneralError
    data class InvalidPassword(val reason: PasswordValidationError) : SignupGeneralError
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
