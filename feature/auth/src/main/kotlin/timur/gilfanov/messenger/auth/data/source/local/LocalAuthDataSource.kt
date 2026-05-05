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
    suspend fun getAccessToken(): ResultWithError<String?, AuthLocalDataSourceError>
    suspend fun getRefreshToken(): ResultWithError<String?, AuthLocalDataSourceError>
    suspend fun getAuthProvider(): ResultWithError<AuthProvider?, AuthLocalDataSourceError>
    suspend fun saveTokens(tokens: AuthTokens): ResultWithError<Unit, AuthLocalDataSourceError>
    suspend fun saveSession(session: AuthSession): ResultWithError<Unit, AuthLocalDataSourceError>
    suspend fun clearSession(): ResultWithError<Unit, AuthLocalDataSourceError>
}
