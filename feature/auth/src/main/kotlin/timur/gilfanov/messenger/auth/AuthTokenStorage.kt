package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

/**
 * Defines the contract for persisting and retrieving authentication tokens.
 *
 * Implementations are responsible for secure, durable local storage of tokens
 * across app restarts. All operations are suspend to accommodate implementations that read from
 * or write to disk.
 */
interface AuthTokenStorage {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun saveTokens(tokens: AuthTokens)
    suspend fun clearTokens()
}
