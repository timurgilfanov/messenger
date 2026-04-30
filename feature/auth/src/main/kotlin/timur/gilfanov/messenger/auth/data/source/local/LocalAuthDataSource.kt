package timur.gilfanov.messenger.auth.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

/**
 * Persistent storage for authentication session data.
 *
 * All operations return [ResultWithError] — callers handle storage errors explicitly.
 * [saveSession] is atomic and should be used for login/signup.
 * [saveTokens] updates only the tokens (for token refresh).
 */
interface LocalAuthDataSource {
    suspend fun getAccessToken(): ResultWithError<String?, LocalAuthDataSourceError>
    suspend fun getRefreshToken(): ResultWithError<String?, LocalAuthDataSourceError>
    suspend fun getAuthProvider(): ResultWithError<AuthProvider?, LocalAuthDataSourceError>
    suspend fun saveTokens(tokens: AuthTokens): ResultWithError<Unit, LocalAuthDataSourceError>
    suspend fun saveSession(session: AuthSession): ResultWithError<Unit, LocalAuthDataSourceError>
    suspend fun clearSession(): ResultWithError<Unit, LocalAuthDataSourceError>
}
