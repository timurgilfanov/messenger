package timur.gilfanov.messenger.auth.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

class LocalAuthDataSourceFake : LocalAuthDataSource {

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var authProvider: AuthProvider? = null

    private val getAccessTokenQueue =
        ArrayDeque<ResultWithError<String?, LocalAuthDataSourceError>>()
    private val getRefreshTokenQueue =
        ArrayDeque<ResultWithError<String?, LocalAuthDataSourceError>>()
    private val getAuthProviderQueue =
        ArrayDeque<ResultWithError<AuthProvider?, LocalAuthDataSourceError>>()
    private val saveTokensQueue = ArrayDeque<ResultWithError<Unit, LocalAuthDataSourceError>>()
    private val saveSessionQueue = ArrayDeque<ResultWithError<Unit, LocalAuthDataSourceError>>()
    private val clearSessionQueue = ArrayDeque<ResultWithError<Unit, LocalAuthDataSourceError>>()

    fun enqueueGetAccessToken(vararg results: ResultWithError<String?, LocalAuthDataSourceError>) {
        results.forEach { getAccessTokenQueue.addLast(it) }
    }

    fun enqueueGetRefreshToken(vararg results: ResultWithError<String?, LocalAuthDataSourceError>) {
        results.forEach { getRefreshTokenQueue.addLast(it) }
    }

    fun enqueueGetAuthProvider(
        vararg results: ResultWithError<AuthProvider?, LocalAuthDataSourceError>,
    ) {
        results.forEach { getAuthProviderQueue.addLast(it) }
    }

    fun enqueueSaveTokens(vararg results: ResultWithError<Unit, LocalAuthDataSourceError>) {
        results.forEach { saveTokensQueue.addLast(it) }
    }

    fun enqueueSaveSession(vararg results: ResultWithError<Unit, LocalAuthDataSourceError>) {
        results.forEach { saveSessionQueue.addLast(it) }
    }

    fun enqueueClearSession(vararg results: ResultWithError<Unit, LocalAuthDataSourceError>) {
        results.forEach { clearSessionQueue.addLast(it) }
    }

    override suspend fun getAccessToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        if (getAccessTokenQueue.isNotEmpty()) {
            getAccessTokenQueue.removeFirst()
        } else {
            ResultWithError.Success(accessToken)
        }

    override suspend fun getRefreshToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        if (getRefreshTokenQueue.isNotEmpty()) {
            getRefreshTokenQueue.removeFirst()
        } else {
            ResultWithError.Success(refreshToken)
        }

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        LocalAuthDataSourceError,
        > =
        if (getAuthProviderQueue.isNotEmpty()) {
            getAuthProviderQueue.removeFirst()
        } else {
            ResultWithError.Success(authProvider)
        }

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, LocalAuthDataSourceError> {
        val result = if (saveTokensQueue.isNotEmpty()) {
            saveTokensQueue.removeFirst()
        } else {
            ResultWithError.Success(
                Unit,
            )
        }
        if (result is ResultWithError.Success) {
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
        }
        return result
    }

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, LocalAuthDataSourceError> {
        val result = if (saveSessionQueue.isNotEmpty()) {
            saveSessionQueue.removeFirst()
        } else {
            ResultWithError.Success(
                Unit,
            )
        }
        if (result is ResultWithError.Success) {
            accessToken = session.tokens.accessToken
            refreshToken = session.tokens.refreshToken
            authProvider = session.provider
        }
        return result
    }

    override suspend fun clearSession(): ResultWithError<Unit, LocalAuthDataSourceError> {
        val result = if (clearSessionQueue.isNotEmpty()) {
            clearSessionQueue.removeFirst()
        } else {
            ResultWithError.Success(
                Unit,
            )
        }
        if (result is ResultWithError.Success) {
            accessToken = null
            refreshToken = null
            authProvider = null
        }
        return result
    }
}
