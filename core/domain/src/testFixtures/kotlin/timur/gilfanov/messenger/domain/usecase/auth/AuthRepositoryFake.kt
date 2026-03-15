package timur.gilfanov.messenger.domain.usecase.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Authenticated
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError

@Suppress("TooManyFunctions") // it's ok for the Fake with enqueue results pattern
class AuthRepositoryFake(initialAuthState: AuthState = Unauthenticated) : AuthRepository {

    private val authStateFlow = MutableStateFlow(initialAuthState)
    override val authState: Flow<AuthState> = authStateFlow.asStateFlow()
    val currentAuthState: AuthState get() = authStateFlow.value

    private val loginWithCredentialsQueue =
        ArrayDeque<ResultWithError<AuthSession, LoginRepositoryError>>()
    private val loginWithGoogleQueue =
        ArrayDeque<ResultWithError<AuthSession, GoogleLoginRepositoryError>>()
    private val signupQueue = ArrayDeque<ResultWithError<AuthSession, SignupRepositoryError>>()
    private val logoutQueue = ArrayDeque<ResultWithError<Unit, LogoutRepositoryError>>()
    private val refreshQueue = ArrayDeque<ResultWithError<AuthTokens, RefreshRepositoryError>>()

    var defaultLoginWithCredentialsResult: ResultWithError<AuthSession, LoginRepositoryError>? =
        null
    var defaultLoginWithGoogleResult: ResultWithError<AuthSession, GoogleLoginRepositoryError>? =
        null
    var defaultSignupResult: ResultWithError<AuthSession, SignupRepositoryError>? = null
    var defaultLogoutResult: ResultWithError<Unit, LogoutRepositoryError>? = null
    var defaultRefreshTokenResult: ResultWithError<AuthTokens, RefreshRepositoryError>? = null

    constructor(initialSession: AuthSession) : this(Authenticated(initialSession))

    fun enqueueLoginWithCredentialsResult(
        vararg results: ResultWithError<AuthSession, LoginRepositoryError>,
    ) {
        results.forEach { loginWithCredentialsQueue.addLast(it) }
    }

    fun enqueueLoginWithGoogleResult(
        vararg results: ResultWithError<AuthSession, GoogleLoginRepositoryError>,
    ) {
        results.forEach { loginWithGoogleQueue.addLast(it) }
    }

    fun enqueueSignupResult(vararg results: ResultWithError<AuthSession, SignupRepositoryError>) {
        results.forEach { signupQueue.addLast(it) }
    }

    fun enqueueLogoutResult(vararg results: ResultWithError<Unit, LogoutRepositoryError>) {
        results.forEach { logoutQueue.addLast(it) }
    }

    fun enqueueRefreshTokenResult(
        vararg results: ResultWithError<AuthTokens, RefreshRepositoryError>,
    ) {
        results.forEach { refreshQueue.addLast(it) }
    }

    private var tokenCounter: Int = 0

    private fun nextTokens(prefix: String): AuthTokens {
        tokenCounter += 1
        return AuthTokens(
            accessToken = "$prefix-access-$tokenCounter",
            refreshToken = "$prefix-refresh-$tokenCounter",
        )
    }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthSession, LoginRepositoryError> {
        val result = if (loginWithCredentialsQueue.isNotEmpty()) {
            loginWithCredentialsQueue.removeFirst()
        } else {
            defaultLoginWithCredentialsResult ?: ResultWithError.Success(
                AuthSession(
                    tokens = nextTokens("credentials-login"),
                    provider = AuthProvider.EMAIL,
                ),
            )
        }
        if (result is ResultWithError.Success) {
            authStateFlow.value = Authenticated(result.data)
        }
        return result
    }

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthSession, GoogleLoginRepositoryError> {
        val result = if (loginWithGoogleQueue.isNotEmpty()) {
            loginWithGoogleQueue.removeFirst()
        } else {
            defaultLoginWithGoogleResult ?: ResultWithError.Success(
                AuthSession(
                    tokens = nextTokens("google-login"),
                    provider = AuthProvider.GOOGLE,
                ),
            )
        }
        if (result is ResultWithError.Success) {
            authStateFlow.value = Authenticated(result.data)
        }
        return result
    }

    override suspend fun signup(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthSession, SignupRepositoryError> {
        val result = if (signupQueue.isNotEmpty()) {
            signupQueue.removeFirst()
        } else {
            defaultSignupResult ?: ResultWithError.Success(
                AuthSession(
                    tokens = nextTokens("signup"),
                    provider = AuthProvider.EMAIL,
                ),
            )
        }
        if (result is ResultWithError.Success) {
            authStateFlow.value = Authenticated(result.data)
        }
        return result
    }

    override suspend fun logout(): ResultWithError<Unit, LogoutRepositoryError> {
        val result = if (logoutQueue.isNotEmpty()) {
            logoutQueue.removeFirst()
        } else {
            defaultLogoutResult ?: ResultWithError.Success(Unit)
        }
        if (result is ResultWithError.Success) {
            authStateFlow.value = Unauthenticated
        }
        return result
    }

    override suspend fun refreshToken(): ResultWithError<AuthTokens, RefreshRepositoryError> {
        val currentState = authStateFlow.value
        if (currentState !is Authenticated) {
            // When unauthenticated, refresh is not allowed. Do not consume scripted outcomes.
            return ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
        }

        val result = if (refreshQueue.isNotEmpty()) {
            refreshQueue.removeFirst()
        } else {
            defaultRefreshTokenResult ?: ResultWithError.Success(nextTokens("refresh"))
        }

        if (result is ResultWithError.Success) {
            authStateFlow.value =
                currentState.copy(session = currentState.session.copy(tokens = result.data))
        }
        return result
    }
}
