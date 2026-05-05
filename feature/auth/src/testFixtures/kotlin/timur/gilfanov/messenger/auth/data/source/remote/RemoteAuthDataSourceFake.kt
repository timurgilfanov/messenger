package timur.gilfanov.messenger.auth.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

class RemoteAuthDataSourceFake : RemoteAuthDataSource {

    private val loginWithCredentialsQueue =
        ArrayDeque<ResultWithError<AuthTokens, LoginWithCredentialsRemoteDataSourceError>>()
    private val loginWithGoogleQueue =
        ArrayDeque<ResultWithError<AuthTokens, LoginWithGoogleRemoteDataSourceError>>()
    private val signupWithGoogleQueue =
        ArrayDeque<ResultWithError<AuthTokens, SignupWithGoogleRemoteDataSourceError>>()
    private val registerQueue =
        ArrayDeque<ResultWithError<AuthTokens, RegisterRemoteDataSourceError>>()
    private val refreshQueue =
        ArrayDeque<ResultWithError<AuthTokens, RefreshRemoteDataSourceError>>()
    private val logoutQueue = ArrayDeque<ResultWithError<Unit, LogoutRemoteDataSourceError>>()

    private var tokenCounter = 0

    private fun nextTokens(prefix: String): AuthTokens {
        tokenCounter += 1
        return AuthTokens(
            accessToken = "$prefix-access-$tokenCounter",
            refreshToken = "$prefix-refresh-$tokenCounter",
        )
    }

    fun enqueueLoginWithCredentials(
        vararg results: ResultWithError<AuthTokens, LoginWithCredentialsRemoteDataSourceError>,
    ) {
        results.forEach { loginWithCredentialsQueue.addLast(it) }
    }

    fun enqueueLoginWithGoogle(
        vararg results: ResultWithError<AuthTokens, LoginWithGoogleRemoteDataSourceError>,
    ) {
        results.forEach { loginWithGoogleQueue.addLast(it) }
    }

    fun enqueueSignupWithGoogle(
        vararg results: ResultWithError<AuthTokens, SignupWithGoogleRemoteDataSourceError>,
    ) {
        results.forEach { signupWithGoogleQueue.addLast(it) }
    }

    fun enqueueRegister(
        vararg results: ResultWithError<AuthTokens, RegisterRemoteDataSourceError>,
    ) {
        results.forEach { registerQueue.addLast(it) }
    }

    fun enqueueRefresh(vararg results: ResultWithError<AuthTokens, RefreshRemoteDataSourceError>) {
        results.forEach { refreshQueue.addLast(it) }
    }

    fun enqueueLogout(vararg results: ResultWithError<Unit, LogoutRemoteDataSourceError>) {
        results.forEach { logoutQueue.addLast(it) }
    }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsRemoteDataSourceError> =
        if (loginWithCredentialsQueue.isNotEmpty()) {
            loginWithCredentialsQueue.removeFirst()
        } else {
            ResultWithError.Success(nextTokens("credentials"))
        }

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleRemoteDataSourceError> =
        if (loginWithGoogleQueue.isNotEmpty()) {
            loginWithGoogleQueue.removeFirst()
        } else {
            ResultWithError.Success(nextTokens("google"))
        }

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleRemoteDataSourceError> =
        if (signupWithGoogleQueue.isNotEmpty()) {
            signupWithGoogleQueue.removeFirst()
        } else {
            ResultWithError.Success(nextTokens("google-signup"))
        }

    override suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterRemoteDataSourceError> =
        if (registerQueue.isNotEmpty()) {
            registerQueue.removeFirst()
        } else {
            ResultWithError.Success(nextTokens("register"))
        }

    override suspend fun refresh(
        refreshToken: String,
    ): ResultWithError<AuthTokens, RefreshRemoteDataSourceError> = if (refreshQueue.isNotEmpty()) {
        refreshQueue.removeFirst()
    } else {
        ResultWithError.Success(nextTokens("refresh"))
    }

    override suspend fun logout(
        accessToken: String,
    ): ResultWithError<Unit, LogoutRemoteDataSourceError> = if (logoutQueue.isNotEmpty()) {
        logoutQueue.removeFirst()
    } else {
        ResultWithError.Success(Unit)
    }
}
