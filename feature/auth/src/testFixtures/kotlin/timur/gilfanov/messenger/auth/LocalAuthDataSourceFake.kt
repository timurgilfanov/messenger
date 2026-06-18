package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.auth.data.source.local.AuthLocalDataSourceError
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

class LocalAuthDataSourceFake : LocalAuthDataSource {
    var accessToken: String? = null
    var refreshToken: String? = null
    var authProvider: AuthProvider? = null
    var sessionId: String? = null

    override suspend fun getAccessToken(): ResultWithError<String?, AuthLocalDataSourceError> =
        ResultWithError.Success(accessToken)

    override suspend fun getRefreshToken(): ResultWithError<String?, AuthLocalDataSourceError> =
        ResultWithError.Success(refreshToken)

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        AuthLocalDataSourceError,
        > =
        ResultWithError.Success(authProvider)

    override suspend fun getSessionId(): ResultWithError<String?, AuthLocalDataSourceError> =
        ResultWithError.Success(sessionId)

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, AuthLocalDataSourceError> {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        return ResultWithError.Success(Unit)
    }

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, AuthLocalDataSourceError> {
        accessToken = session.tokens.accessToken
        refreshToken = session.tokens.refreshToken
        authProvider = session.provider
        sessionId = session.sessionId
        return ResultWithError.Success(Unit)
    }

    override suspend fun clearSession(): ResultWithError<Unit, AuthLocalDataSourceError> {
        accessToken = null
        refreshToken = null
        authProvider = null
        sessionId = null
        return ResultWithError.Success(Unit)
    }
}
