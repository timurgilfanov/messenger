package timur.gilfanov.messenger.auth.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithCredentialsError
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithGoogleError
import timur.gilfanov.messenger.auth.data.source.remote.LogoutError
import timur.gilfanov.messenger.auth.data.source.remote.RefreshError
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSource
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorage
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorageError
import timur.gilfanov.messenger.auth.di.ApplicationScope
import timur.gilfanov.messenger.data.remote.toRemoteError
import timur.gilfanov.messenger.data.remote.toUnauthRemoteError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.util.Logger

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteAuthDataSource,
    private val sessionStorage: AuthSessionStorage,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val logger: Logger,
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepositoryImpl"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        coroutineScope.launch { restoreAuthState() }
    }

    private suspend fun restoreAuthState() {
        val accessToken = sessionStorage.getAccessToken().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore access token: $e")
                null
            },
        )
        val refreshToken = sessionStorage.getRefreshToken().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore refresh token: $e")
                null
            },
        )
        val provider = sessionStorage.getAuthProvider().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore auth provider: $e")
                null
            },
        )
        if (accessToken != null && refreshToken != null && provider != null) {
            _authState.value = AuthState.Authenticated(
                AuthSession(
                    tokens = AuthTokens(accessToken, refreshToken),
                    provider = provider,
                ),
            )
        }
    }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthSession, LoginRepositoryError> =
        remoteDataSource.loginWithCredentials(credentials).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.EMAIL)
                sessionStorage.saveSession(session).fold(
                    onSuccess = {
                        _authState.value = AuthState.Authenticated(session)
                        ResultWithError.Success(session)
                    },
                    onFailure = { storageError ->
                        logger.e(TAG, "Failed to save session after login: $storageError")
                        ResultWithError.Failure(
                            LoginRepositoryError.LocalOperationFailed(storageError.toLocalError()),
                        )
                    },
                )
            },
            onFailure = { error ->
                ResultWithError.Failure(mapLoginWithCredentialsError(error))
            },
        )

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthSession, GoogleLoginRepositoryError> =
        remoteDataSource.loginWithGoogle(idToken).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.GOOGLE)
                sessionStorage.saveSession(session).fold(
                    onSuccess = {
                        _authState.value = AuthState.Authenticated(session)
                        ResultWithError.Success(session)
                    },
                    onFailure = { storageError ->
                        logger.e(TAG, "Failed to save session after Google login: $storageError")
                        ResultWithError.Failure(
                            GoogleLoginRepositoryError.LocalOperationFailed(
                                storageError.toLocalError(),
                            ),
                        )
                    },
                )
            },
            onFailure = { error ->
                ResultWithError.Failure(mapLoginWithGoogleError(error))
            },
        )

    override suspend fun signup(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthSession, SignupRepositoryError> =
        remoteDataSource.register(credentials, name).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.EMAIL)
                sessionStorage.saveSession(session).fold(
                    onSuccess = {
                        _authState.value = AuthState.Authenticated(session)
                        ResultWithError.Success(session)
                    },
                    onFailure = { storageError ->
                        logger.e(TAG, "Failed to save session after signup: $storageError")
                        ResultWithError.Failure(
                            SignupRepositoryError.LocalOperationFailed(storageError.toLocalError()),
                        )
                    },
                )
            },
            onFailure = { error ->
                ResultWithError.Failure(mapSignupError(error))
            },
        )

    override suspend fun logout(): ResultWithError<Unit, LogoutRepositoryError> {
        val accessToken = sessionStorage.getAccessToken().fold(
            onSuccess = { it },
            onFailure = { null },
        )
        val remoteResult = if (accessToken != null) {
            remoteDataSource.logout(accessToken).fold(
                onSuccess = { null },
                onFailure = { error ->
                    logger.e(TAG, "Remote logout failed: $error")
                    mapLogoutError(error)
                },
            )
        } else {
            null
        }
        sessionStorage.clearSession().fold(
            onSuccess = {},
            onFailure = { storageError ->
                logger.e(TAG, "Failed to clear session on logout: $storageError")
            },
        )
        _authState.value = AuthState.Unauthenticated
        return if (remoteResult != null) {
            ResultWithError.Failure(remoteResult)
        } else {
            ResultWithError.Success(Unit)
        }
    }

    override suspend fun refreshToken(): ResultWithError<AuthTokens, RefreshRepositoryError> =
        sessionStorage.getRefreshToken().fold(
            onSuccess = { refreshToken ->
                if (refreshToken == null) {
                    ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
                } else {
                    remoteDataSource.refresh(refreshToken).fold(
                        onSuccess = { newTokens ->
                            sessionStorage.saveTokens(newTokens).fold(
                                onSuccess = {
                                    val currentState = _authState.value
                                    if (currentState is AuthState.Authenticated) {
                                        _authState.value = currentState.copy(
                                            session = currentState.session.copy(tokens = newTokens),
                                        )
                                    }
                                    ResultWithError.Success(newTokens)
                                },
                                onFailure = { storageError ->
                                    logger.e(TAG, "Failed to save refreshed tokens: $storageError")
                                    ResultWithError.Failure(
                                        RefreshRepositoryError.LocalOperationFailed(
                                            storageError.toLocalError(),
                                        ),
                                    )
                                },
                            )
                        },
                        onFailure = { error ->
                            ResultWithError.Failure(mapRefreshError(error))
                        },
                    )
                }
            },
            onFailure = { storageError ->
                logger.e(TAG, "Failed to get refresh token: $storageError")
                ResultWithError.Failure(
                    RefreshRepositoryError.LocalOperationFailed(storageError.toLocalError()),
                )
            },
        )
}

private fun AuthSessionStorageError.toLocalError(): LocalStorageError = when (this) {
    AuthSessionStorageError.AccessDenied -> LocalStorageError.AccessDenied
    AuthSessionStorageError.KeystoreUnavailable -> LocalStorageError.TemporarilyUnavailable
    AuthSessionStorageError.DataCorrupted -> LocalStorageError.Corrupted
    is AuthSessionStorageError.UnknownError -> LocalStorageError.UnknownError(cause)
}

private fun mapLoginWithCredentialsError(error: LoginWithCredentialsError): LoginRepositoryError =
    when (error) {
        LoginWithCredentialsError.InvalidCredentials -> LoginRepositoryError.InvalidCredentials

        LoginWithCredentialsError.EmailNotVerified -> LoginRepositoryError.EmailNotVerified

        LoginWithCredentialsError.AccountSuspended -> LoginRepositoryError.AccountSuspended

        is LoginWithCredentialsError.RemoteDataSource ->
            LoginRepositoryError.RemoteOperationFailed(error.error.toUnauthRemoteError())
    }

private fun mapLoginWithGoogleError(error: LoginWithGoogleError): GoogleLoginRepositoryError =
    when (error) {
        LoginWithGoogleError.InvalidToken -> GoogleLoginRepositoryError.InvalidToken

        LoginWithGoogleError.AccountNotFound -> GoogleLoginRepositoryError.AccountNotFound

        LoginWithGoogleError.AccountSuspended -> GoogleLoginRepositoryError.AccountSuspended

        is LoginWithGoogleError.RemoteDataSource ->
            GoogleLoginRepositoryError.RemoteOperationFailed(error.error.toUnauthRemoteError())
    }

private fun mapSignupError(error: RegisterError): SignupRepositoryError = when (error) {
    is RegisterError.InvalidEmail -> SignupRepositoryError.InvalidEmail(error.reason)

    is RegisterError.InvalidPassword -> SignupRepositoryError.InvalidPassword(error.reason)

    is RegisterError.InvalidName -> SignupRepositoryError.InvalidName(error.reason)

    is RegisterError.RemoteDataSource ->
        SignupRepositoryError.RemoteOperationFailed(error.error.toUnauthRemoteError())
}

private fun mapRefreshError(error: RefreshError): RefreshRepositoryError = when (error) {
    RefreshError.TokenExpired -> RefreshRepositoryError.TokenExpired

    RefreshError.SessionRevoked -> RefreshRepositoryError.SessionRevoked

    is RefreshError.RemoteDataSource ->
        RefreshRepositoryError.RemoteOperationFailed(error.error.toRemoteError())
}

private fun mapLogoutError(error: LogoutError): LogoutRepositoryError = when (error) {
    is LogoutError.RemoteDataSource ->
        LogoutRepositoryError.RemoteOperationFailed(error.error.toRemoteError())
}
