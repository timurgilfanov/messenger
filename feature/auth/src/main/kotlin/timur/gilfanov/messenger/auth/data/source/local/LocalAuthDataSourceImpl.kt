package timur.gilfanov.messenger.auth.data.source.local

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.profile.UserId

private const val FILE_NAME = "auth_session"

@Suppress("TooManyFunctions") // will be removed in next commit
@Singleton
class LocalAuthDataSourceImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val authSessionCipher: AuthSessionCipher,
) : LocalAuthDataSource {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        authSessionCipher: AuthSessionCipherImpl,
    ) : this(
        dataStore = createDataStore(context),
        authSessionCipher = authSessionCipher,
    )

    private val keyAccessToken = stringPreferencesKey("access_token")
    private val keyRefreshToken = stringPreferencesKey("refresh_token")
    private val keyAuthProvider = stringPreferencesKey("auth_provider")
    private val keyUserId = stringPreferencesKey("user_id")

    override suspend fun getAccessToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        getToken(keyAccessToken)

    override suspend fun getRefreshToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        getToken(keyRefreshToken)

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        LocalAuthDataSourceError,
        > =
        readPreference(keyAuthProvider).fold(
            onSuccess = { providerName ->
                if (providerName == null) {
                    ResultWithError.Success(null)
                } else {
                    try {
                        ResultWithError.Success(AuthProvider.valueOf(providerName))
                    } catch (_: IllegalArgumentException) {
                        ResultWithError.Failure(LocalAuthDataSourceError.DataCorrupted)
                    }
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override suspend fun getUserId(): ResultWithError<UserId?, LocalAuthDataSourceError> =
        readPreference(keyUserId).fold(
            onSuccess = { userIdString ->
                if (userIdString == null) {
                    ResultWithError.Success(null)
                } else {
                    try {
                        ResultWithError.Success(UserId(UUID.fromString(userIdString)))
                    } catch (_: IllegalArgumentException) {
                        ResultWithError.Failure(LocalAuthDataSourceError.DataCorrupted)
                    }
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, LocalAuthDataSourceError> =
        encryptTokenPair(tokens.accessToken, tokens.refreshToken).fold(
            onSuccess = { (encryptedAccessToken, encryptedRefreshToken) ->
                writePreferences { preferences ->
                    preferences[keyAccessToken] = encryptedAccessToken
                    preferences[keyRefreshToken] = encryptedRefreshToken
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, LocalAuthDataSourceError> =
        encryptTokenPair(session.tokens.accessToken, session.tokens.refreshToken).fold(
            onSuccess = { (encryptedAccessToken, encryptedRefreshToken) ->
                writePreferences { preferences ->
                    preferences[keyAccessToken] = encryptedAccessToken
                    preferences[keyRefreshToken] = encryptedRefreshToken
                    preferences[keyAuthProvider] = session.provider.name
                    preferences[keyUserId] = session.userId.id.toString()
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override suspend fun clearSession(): ResultWithError<Unit, LocalAuthDataSourceError> =
        writePreferences { it.clear() }

    private suspend fun getToken(
        key: Preferences.Key<String>,
    ): ResultWithError<String?, LocalAuthDataSourceError> = readPreference(key).fold(
        onSuccess = { encodedToken ->
            if (encodedToken == null) {
                ResultWithError.Success<String?, LocalAuthDataSourceError>(null)
            } else {
                when (val decryptResult = authSessionCipher.decrypt(encodedToken).toLocalResult()) {
                    is ResultWithError.Success ->
                        ResultWithError.Success<String?, LocalAuthDataSourceError>(
                            decryptResult.data,
                        )

                    is ResultWithError.Failure ->
                        ResultWithError.Failure<String?, LocalAuthDataSourceError>(
                            decryptResult.error,
                        )
                }
            }
        },
        onFailure = { ResultWithError.Failure(it) },
    )

    private fun encryptTokenPair(
        accessToken: String,
        refreshToken: String,
    ): ResultWithError<Pair<String, String>, LocalAuthDataSourceError> =
        authSessionCipher.encrypt(accessToken).toLocalResult().fold(
            onSuccess = { encryptedAccessToken ->
                authSessionCipher.encrypt(refreshToken).toLocalResult().fold(
                    onSuccess = { encryptedRefreshToken ->
                        ResultWithError.Success(encryptedAccessToken to encryptedRefreshToken)
                    },
                    onFailure = { ResultWithError.Failure(it) },
                )
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    private suspend fun readPreference(
        key: Preferences.Key<String>,
    ): ResultWithError<String?, LocalAuthDataSourceError> = runCatching {
        dataStore.data.first()[key]
    }.fold(
        onSuccess = { ResultWithError.Success(it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            ResultWithError.Failure(error.toLocalAuthDataSourceError())
        },
    )

    private suspend fun writePreferences(
        update: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): ResultWithError<Unit, LocalAuthDataSourceError> = runCatching {
        dataStore.edit { preferences ->
            update(preferences)
        }
    }.fold(
        onSuccess = { ResultWithError.Success(Unit) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            ResultWithError.Failure(error.toLocalAuthDataSourceError())
        },
    )
}

private fun createDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }

private fun <T> ResultWithError<T, AuthSessionCipherError>.toLocalResult():
    ResultWithError<T, LocalAuthDataSourceError> =
    when (this) {
        is ResultWithError.Success -> ResultWithError.Success(data)
        is ResultWithError.Failure -> ResultWithError.Failure(error.toLocalError())
    }

private fun AuthSessionCipherError.toLocalError(): LocalAuthDataSourceError = when (this) {
    AuthSessionCipherError.AccessDenied -> LocalAuthDataSourceError.AccessDenied
    AuthSessionCipherError.KeystoreUnavailable -> LocalAuthDataSourceError.KeystoreUnavailable
    AuthSessionCipherError.DataCorrupted -> LocalAuthDataSourceError.DataCorrupted
    is AuthSessionCipherError.UnknownError -> LocalAuthDataSourceError.UnknownError(cause)
}

private fun Throwable.toLocalAuthDataSourceError(): LocalAuthDataSourceError {
    val causes = generateSequence(this) { it.cause }.toList()

    return when {
        causes.any { it is CorruptionException } -> LocalAuthDataSourceError.DataCorrupted
        causes.any(::isStorageFullThrowable) -> LocalAuthDataSourceError.StorageFull
        causes.any(::isReadOnlyThrowable) -> LocalAuthDataSourceError.ReadOnly
        causes.any(::isAccessDeniedThrowable) -> LocalAuthDataSourceError.AccessDenied
        causes.any { it is IOException } -> LocalAuthDataSourceError.TemporarilyUnavailable
        else -> LocalAuthDataSourceError.UnknownError(this)
    }
}

private fun isStorageFullThrowable(throwable: Throwable): Boolean =
    isErrnoThrowable(throwable, OsConstants.ENOSPC) ||
        throwable.message.containsAnyIgnoreCase(
            "no space left on device",
            "enospc",
            "disk full",
        )

private fun isReadOnlyThrowable(throwable: Throwable): Boolean =
    isErrnoThrowable(throwable, OsConstants.EROFS) ||
        throwable.message.containsAnyIgnoreCase(
            "read-only file system",
            "erofs",
        )

private fun isAccessDeniedThrowable(throwable: Throwable): Boolean =
    throwable is SecurityException ||
        isErrnoThrowable(throwable, OsConstants.EACCES, OsConstants.EPERM) ||
        throwable.message.containsAnyIgnoreCase(
            "permission denied",
            "operation not permitted",
            "eacces",
            "eperm",
        )

private fun isErrnoThrowable(throwable: Throwable, vararg errnoValues: Int): Boolean =
    throwable is ErrnoException && errnoValues.any { throwable.errno == it }

private fun String?.containsAnyIgnoreCase(vararg values: String): Boolean =
    this != null && values.any { contains(it, ignoreCase = true) }
