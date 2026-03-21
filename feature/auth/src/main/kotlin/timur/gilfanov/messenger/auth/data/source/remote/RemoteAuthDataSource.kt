package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

/**
 * Remote data source for authentication operations.
 *
 * Each method maps server errors to typed [LoginWithCredentialsError], [LoginWithGoogleError],
 * [RegisterError], [RefreshError], or [LogoutError].
 * Infrastructure failures are wrapped in the `RemoteDataSource` variant of each error type.
 */
interface RemoteAuthDataSource {
    suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsError>
    suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleError>
    suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterError>
    suspend fun refresh(refreshToken: String): ResultWithError<AuthTokens, RefreshError>
    suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutError>
}
