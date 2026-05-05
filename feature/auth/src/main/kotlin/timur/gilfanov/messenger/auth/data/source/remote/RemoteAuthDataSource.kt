package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

/**
 * Remote data source for authentication operations.
 *
 * Each method maps server errors to one of the operation-specific auth remote
 * data source error types declared in `RemoteAuthDataSourceError.kt`.
 * Infrastructure failures are wrapped in the `RemoteDataSource` variant of each error type.
 */
interface RemoteAuthDataSource {
    suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsRemoteDataSourceError>
    suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleRemoteDataSourceError>
    suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleRemoteDataSourceError>
    suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterRemoteDataSourceError>
    suspend fun refresh(
        refreshToken: String,
    ): ResultWithError<AuthTokens, RefreshRemoteDataSourceError>
    suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutRemoteDataSourceError>
}
