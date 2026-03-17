package timur.gilfanov.messenger.auth.data.storage

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

class AuthSessionStorageFake : AuthSessionStorage {

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var authProvider: AuthProvider? = null

    private val getAccessTokenQueue =
        ArrayDeque<ResultWithError<String?, AuthSessionStorageError>>()
    private val getRefreshTokenQueue =
        ArrayDeque<ResultWithError<String?, AuthSessionStorageError>>()
    private val getAuthProviderQueue =
        ArrayDeque<ResultWithError<AuthProvider?, AuthSessionStorageError>>()
    private val saveTokensQueue = ArrayDeque<ResultWithError<Unit, AuthSessionStorageError>>()
    private val saveSessionQueue = ArrayDeque<ResultWithError<Unit, AuthSessionStorageError>>()
    private val clearSessionQueue = ArrayDeque<ResultWithError<Unit, AuthSessionStorageError>>()

    fun enqueueGetAccessToken(vararg results: ResultWithError<String?, AuthSessionStorageError>) {
        results.forEach { getAccessTokenQueue.addLast(it) }
    }

    fun enqueueGetRefreshToken(vararg results: ResultWithError<String?, AuthSessionStorageError>) {
        results.forEach { getRefreshTokenQueue.addLast(it) }
    }

    fun enqueueGetAuthProvider(
        vararg results: ResultWithError<AuthProvider?, AuthSessionStorageError>,
    ) {
        results.forEach { getAuthProviderQueue.addLast(it) }
    }

    fun enqueueSaveTokens(vararg results: ResultWithError<Unit, AuthSessionStorageError>) {
        results.forEach { saveTokensQueue.addLast(it) }
    }

    fun enqueueSaveSession(vararg results: ResultWithError<Unit, AuthSessionStorageError>) {
        results.forEach { saveSessionQueue.addLast(it) }
    }

    fun enqueueClearSession(vararg results: ResultWithError<Unit, AuthSessionStorageError>) {
        results.forEach { clearSessionQueue.addLast(it) }
    }

    override suspend fun getAccessToken(): ResultWithError<String?, AuthSessionStorageError> =
        if (getAccessTokenQueue.isNotEmpty()) {
            getAccessTokenQueue.removeFirst()
        } else {
            ResultWithError.Success(accessToken)
        }

    override suspend fun getRefreshToken(): ResultWithError<String?, AuthSessionStorageError> =
        if (getRefreshTokenQueue.isNotEmpty()) {
            getRefreshTokenQueue.removeFirst()
        } else {
            ResultWithError.Success(refreshToken)
        }

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        AuthSessionStorageError,
        > =
        if (getAuthProviderQueue.isNotEmpty()) {
            getAuthProviderQueue.removeFirst()
        } else {
            ResultWithError.Success(authProvider)
        }

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, AuthSessionStorageError> {
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
    ): ResultWithError<Unit, AuthSessionStorageError> {
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

    override suspend fun clearSession(): ResultWithError<Unit, AuthSessionStorageError> {
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
