package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorage
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorageError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

class AuthSessionStorageFake : AuthSessionStorage {
    var accessToken: String? = null
    var refreshToken: String? = null
    var authProvider: AuthProvider? = null

    override suspend fun getAccessToken(): ResultWithError<String?, AuthSessionStorageError> =
        ResultWithError.Success(accessToken)

    override suspend fun getRefreshToken(): ResultWithError<String?, AuthSessionStorageError> =
        ResultWithError.Success(refreshToken)

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        AuthSessionStorageError,
        > =
        ResultWithError.Success(authProvider)

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, AuthSessionStorageError> {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        return ResultWithError.Success(Unit)
    }

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, AuthSessionStorageError> {
        accessToken = session.tokens.accessToken
        refreshToken = session.tokens.refreshToken
        authProvider = session.provider
        return ResultWithError.Success(Unit)
    }

    override suspend fun clearSession(): ResultWithError<Unit, AuthSessionStorageError> {
        accessToken = null
        refreshToken = null
        authProvider = null
        return ResultWithError.Success(Unit)
    }
}
