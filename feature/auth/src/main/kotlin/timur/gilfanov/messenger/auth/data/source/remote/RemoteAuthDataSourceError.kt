package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

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
    data class InvalidEmail(val reason: EmailValidationError) : RegisterError
    data class InvalidPassword(val reason: PasswordValidationError) : RegisterError
    data class InvalidName(val reason: ProfileNameValidationError) : RegisterError
    data class RemoteDataSource(val error: RemoteDataSourceError) : RegisterError
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
