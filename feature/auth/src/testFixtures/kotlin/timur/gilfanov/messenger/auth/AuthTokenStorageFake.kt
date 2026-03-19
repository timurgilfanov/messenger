package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

class AuthTokenStorageFake : AuthTokenStorage {
    var accessToken: String? = null
    var refreshToken: String? = null

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(tokens: AuthTokens) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    override suspend fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
}
