package timur.gilfanov.messenger.auth.login

import androidx.compose.runtime.Immutable
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val emailError: CredentialsValidationError? = null,
    val passwordError: CredentialsValidationError? = null,
    val generalError: LoginGeneralError? = null,
)

sealed interface LoginGeneralError {
    data object InvalidCredentials : LoginGeneralError
    data object EmailNotVerified : LoginGeneralError
    data object AccountSuspended : LoginGeneralError
    data object InvalidEmail : LoginGeneralError
    data object AccountNotFound : LoginGeneralError
    data object InvalidToken : LoginGeneralError
    data object NetworkUnavailable : LoginGeneralError
    data object ServiceUnavailable : LoginGeneralError
    data object Unknown : LoginGeneralError
    data object GoogleSignInFailed : LoginGeneralError
}
