package timur.gilfanov.messenger.auth.data.source.local

import timur.gilfanov.messenger.domain.UserScopeKey
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

    /**
     * Sets or clears the pending cleanup marker that survives process death.
     * Pass a [UserScopeKey] to persist the marker before [clearSession]; pass null to remove it
     * once cleanup is complete or no longer needed.
     */
    suspend fun setPendingCleanupKey(
        key: UserScopeKey?,
    ): ResultWithError<Unit, LocalAuthDataSourceError>

    /**
     * Writes [key] as the pending cleanup marker only if no marker currently exists.
     * Returns `true` in [ResultWithError.Success] if the marker was written, `false` if a marker
     * was already present (the existing marker is left unchanged). The check and write happen
     * atomically in a single transaction.
     */
    suspend fun setPendingCleanupKeyIfAbsent(
        key: UserScopeKey,
    ): ResultWithError<Boolean, LocalAuthDataSourceError>

    /**
     * Clears the pending cleanup marker if and only if it currently equals [key].
     * The comparison and removal happen atomically in a single transaction.
     */
    suspend fun clearPendingCleanupKeyIfMatches(
        key: UserScopeKey,
    ): ResultWithError<Unit, LocalAuthDataSourceError>

    /**
     * Returns the pending cleanup key written by a previous session, or null if none exists.
     */
    suspend fun getPendingCleanupKey(): ResultWithError<UserScopeKey?, LocalAuthDataSourceError>
}
