package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError

/**
 * Errors for [RemoteAuthDataSource.loginWithCredentials].
 */
sealed interface LoginWithCredentialsError {
    data object InvalidCredentials : LoginWithCredentialsError
    data object EmailNotVerified : LoginWithCredentialsError
    data object AccountSuspended : LoginWithCredentialsError
    data class RemoteDataSource(val error: RemoteDataSourceError) : LoginWithCredentialsError
}

/**
 * Errors for [RemoteAuthDataSource.loginWithGoogle].
 */
sealed interface LoginWithGoogleError {
    data object InvalidToken : LoginWithGoogleError
    data object AccountNotFound : LoginWithGoogleError
    data object AccountSuspended : LoginWithGoogleError
    data class RemoteDataSource(val error: RemoteDataSourceError) : LoginWithGoogleError
}

/**
 * Errors for [RemoteAuthDataSource.register].
 */
sealed interface RegisterError {
    data class InvalidEmail(val reason: SignupEmailError) : RegisterError
    data class InvalidPassword(val reason: PasswordValidationError) : RegisterError
    data class InvalidName(val reason: ProfileNameValidationError) : RegisterError
    data class RemoteDataSource(val error: RemoteDataSourceError) : RegisterError
}

/**
 * Errors for [RemoteAuthDataSource.signupWithGoogle].
 */
sealed interface SignupWithGoogleError {
    data object InvalidToken : SignupWithGoogleError
    data object AccountAlreadyExists : SignupWithGoogleError
    data class InvalidName(val reason: ProfileNameValidationError) : SignupWithGoogleError
    data class RemoteDataSource(val error: RemoteDataSourceError) : SignupWithGoogleError
}

/**
 * Errors for [RemoteAuthDataSource.refresh].
 */
sealed interface RefreshError {
    data object TokenExpired : RefreshError
    data object SessionRevoked : RefreshError
    data class RemoteDataSource(val error: RemoteDataSourceError) : RefreshError
}

/**
 * Errors for [RemoteAuthDataSource.logout].
 */
sealed interface LogoutError {
    data class RemoteDataSource(val error: RemoteDataSourceError) : LogoutError
}
