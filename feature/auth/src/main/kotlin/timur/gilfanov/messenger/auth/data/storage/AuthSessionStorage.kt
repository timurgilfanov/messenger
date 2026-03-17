package timur.gilfanov.messenger.auth.data.storage

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
interface AuthSessionStorage {
    suspend fun getAccessToken(): ResultWithError<String?, AuthSessionStorageError>
    suspend fun getRefreshToken(): ResultWithError<String?, AuthSessionStorageError>
    suspend fun getAuthProvider(): ResultWithError<AuthProvider?, AuthSessionStorageError>
    suspend fun saveTokens(tokens: AuthTokens): ResultWithError<Unit, AuthSessionStorageError>
    suspend fun saveSession(session: AuthSession): ResultWithError<Unit, AuthSessionStorageError>
    suspend fun clearSession(): ResultWithError<Unit, AuthSessionStorageError>
}
