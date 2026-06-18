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
 * [saveTokens] updates only the tokens (for token refresh); it does NOT update [getSessionId].
 */
interface LocalAuthDataSource {
    suspend fun getAccessToken(): ResultWithError<String?, AuthLocalDataSourceError>
    suspend fun getRefreshToken(): ResultWithError<String?, AuthLocalDataSourceError>
    suspend fun getAuthProvider(): ResultWithError<AuthProvider?, AuthLocalDataSourceError>

    /**
     * Returns the stable session identifier persisted at login/signup time, or null if not set.
     * The session ID is preserved across token rotations so that user-scoped data partitions
     * remain stable when [saveTokens] updates the token pair.
     */
    suspend fun getSessionId(): ResultWithError<String?, AuthLocalDataSourceError>

    suspend fun saveTokens(tokens: AuthTokens): ResultWithError<Unit, AuthLocalDataSourceError>
    suspend fun saveSession(session: AuthSession): ResultWithError<Unit, AuthLocalDataSourceError>
    suspend fun clearSession(): ResultWithError<Unit, AuthLocalDataSourceError>
}
