package timur.gilfanov.messenger.auth.data.repository

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceError
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithCredentialsError
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithGoogleError
import timur.gilfanov.messenger.auth.data.source.remote.LogoutError
import timur.gilfanov.messenger.auth.data.source.remote.RefreshError
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSource
import timur.gilfanov.messenger.auth.data.source.remote.SignupWithGoogleError
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
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.util.Logger

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteAuthDataSource,
    private val localDataSource: LocalAuthDataSource,
    private val cleanupObserver: Lazy<AuthCleanupObserver>,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val logger: Logger,
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepositoryImpl"
    }

    private val _authState = MutableStateFlow<AuthState?>(null)
    override val authState: Flow<AuthState> = _authState.filterNotNull()

    init {
        cleanupObserver.get().start()
        coroutineScope.launch {
            restoreAuthState()
        }
    }

    private suspend fun restoreAuthState() {
        val accessToken = localDataSource.getAccessToken().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore access token: $e")
                null
            },
        )
        val refreshToken = localDataSource.getRefreshToken().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore refresh token: $e")
                null
            },
        )
        val provider = localDataSource.getAuthProvider().fold(
            onSuccess = { it },
            onFailure = { e ->
                logger.e(TAG, "Failed to restore auth provider: $e")
                null
            },
        )
        val restoredState = if (accessToken != null && refreshToken != null && provider != null) {
            AuthState.Authenticated(
                AuthSession(
                    tokens = AuthTokens(accessToken, refreshToken),
                    provider = provider,
                ),
            )
        } else {
            AuthState.Unauthenticated
        }
        _authState.compareAndSet(null, restoredState)
    }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthSession, LoginRepositoryError> =
        remoteDataSource.loginWithCredentials(credentials).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.EMAIL)
                localDataSource.saveSession(session).fold(
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
                localDataSource.saveSession(session).fold(
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

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthSession, GoogleSignupRepositoryError> =
        remoteDataSource.signupWithGoogle(idToken, name).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.GOOGLE)
                localDataSource.saveSession(session).fold(
                    onSuccess = {
                        _authState.value = AuthState.Authenticated(session)
                        ResultWithError.Success(session)
                    },
                    onFailure = { storageError ->
                        logger.e(TAG, "Failed to save session after Google signup: $storageError")
                        ResultWithError.Failure(
                            GoogleSignupRepositoryError.LocalOperationFailed(
                                storageError.toLocalError(),
                            ),
                        )
                    },
                )
            },
            onFailure = { error ->
                ResultWithError.Failure(mapSignupWithGoogleError(error))
            },
        )

    override suspend fun signup(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthSession, SignupRepositoryError> =
        remoteDataSource.register(credentials, name).fold(
            onSuccess = { tokens ->
                val session = AuthSession(tokens = tokens, provider = AuthProvider.EMAIL)
                localDataSource.saveSession(session).fold(
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
        val accessToken = localDataSource.getAccessToken().fold(
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
        val clearSessionError = localDataSource.clearSession().fold(
            onSuccess = { null },
            onFailure = { storageError ->
                logger.e(TAG, "Failed to clear session on logout: $storageError")
                LogoutRepositoryError.LocalOperationFailed(storageError.toLocalError())
            },
        )
        if (clearSessionError == null) {
            _authState.value = AuthState.Unauthenticated
        }
        return when {
            clearSessionError != null -> ResultWithError.Failure(clearSessionError)
            remoteResult != null -> ResultWithError.Failure(remoteResult)
            else -> ResultWithError.Success(Unit)
        }
    }

    override suspend fun refreshToken(): ResultWithError<AuthTokens, RefreshRepositoryError> =
        localDataSource.getRefreshToken().fold(
            onSuccess = { refreshToken ->
                if (refreshToken == null) {
                    ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
                } else {
                    remoteDataSource.refresh(refreshToken).fold(
                        onSuccess = { newTokens ->
                            localDataSource.saveTokens(newTokens).fold(
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

private fun LocalAuthDataSourceError.toLocalError(): LocalStorageError = when (this) {
    LocalAuthDataSourceError.AccessDenied -> LocalStorageError.AccessDenied
    LocalAuthDataSourceError.KeystoreUnavailable -> LocalStorageError.TemporarilyUnavailable
    LocalAuthDataSourceError.TemporarilyUnavailable -> LocalStorageError.TemporarilyUnavailable
    LocalAuthDataSourceError.StorageFull -> LocalStorageError.StorageFull
    LocalAuthDataSourceError.ReadOnly -> LocalStorageError.ReadOnly
    LocalAuthDataSourceError.DataCorrupted -> LocalStorageError.Corrupted
    is LocalAuthDataSourceError.UnknownError -> LocalStorageError.UnknownError(cause)
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

private fun mapSignupWithGoogleError(error: SignupWithGoogleError): GoogleSignupRepositoryError =
    when (error) {
        SignupWithGoogleError.InvalidToken -> GoogleSignupRepositoryError.InvalidToken

        SignupWithGoogleError.AccountAlreadyExists ->
            GoogleSignupRepositoryError.AccountAlreadyExists

        is SignupWithGoogleError.InvalidName ->
            GoogleSignupRepositoryError.InvalidName(error.reason)

        is SignupWithGoogleError.RemoteDataSource ->
            GoogleSignupRepositoryError.RemoteOperationFailed(error.error.toUnauthRemoteError())
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
