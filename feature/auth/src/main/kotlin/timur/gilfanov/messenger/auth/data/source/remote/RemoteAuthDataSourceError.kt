package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError

/**
 * Errors for [RemoteAuthDataSource.loginWithCredentials].
 */
sealed interface LoginWithCredentialsRemoteDataSourceError {
    data object InvalidCredentials : LoginWithCredentialsRemoteDataSourceError
    data object EmailNotVerified : LoginWithCredentialsRemoteDataSourceError
    data object AccountSuspended : LoginWithCredentialsRemoteDataSourceError
    data class RemoteDataSource(val error: RemoteDataSourceError) :
        LoginWithCredentialsRemoteDataSourceError
}

/**
 * Errors for [RemoteAuthDataSource.loginWithGoogle].
 */
sealed interface LoginWithGoogleRemoteDataSourceError {
    data object InvalidToken : LoginWithGoogleRemoteDataSourceError
    data object AccountNotFound : LoginWithGoogleRemoteDataSourceError
    data object AccountSuspended : LoginWithGoogleRemoteDataSourceError
    data class RemoteDataSource(val error: RemoteDataSourceError) :
        LoginWithGoogleRemoteDataSourceError
}

/**
 * Errors for [RemoteAuthDataSource.register].
 */
sealed interface RegisterRemoteDataSourceError {
    data class InvalidEmail(val reason: SignupEmailError) : RegisterRemoteDataSourceError
    data class InvalidPassword(val reason: PasswordValidationError) : RegisterRemoteDataSourceError
    data class InvalidName(val reason: ProfileNameValidationError) : RegisterRemoteDataSourceError
    data class RemoteDataSource(val error: RemoteDataSourceError) : RegisterRemoteDataSourceError
}

/**
 * Errors for [RemoteAuthDataSource.signupWithGoogle].
 */
sealed interface SignupWithGoogleRemoteDataSourceError {
    data object InvalidToken : SignupWithGoogleRemoteDataSourceError
    data object AccountAlreadyExists : SignupWithGoogleRemoteDataSourceError
    data class InvalidName(val reason: ProfileNameValidationError) :
        SignupWithGoogleRemoteDataSourceError
    data class RemoteDataSource(val error: RemoteDataSourceError) :
        SignupWithGoogleRemoteDataSourceError
}

/**
 * Errors for [RemoteAuthDataSource.refresh].
 */
sealed interface RefreshRemoteDataSourceError {
    data object TokenExpired : RefreshRemoteDataSourceError
    data object SessionRevoked : RefreshRemoteDataSourceError
    data class RemoteDataSource(val error: RemoteDataSourceError) : RefreshRemoteDataSourceError
}

/**
 * Errors for [RemoteAuthDataSource.logout].
 */
sealed interface LogoutRemoteDataSourceError {
    data class RemoteDataSource(val error: RemoteDataSourceError) : LogoutRemoteDataSourceError
}
