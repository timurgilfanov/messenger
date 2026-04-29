package timur.gilfanov.messenger.auth.data.source.local

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.fold

private const val FILE_NAME = "auth_session"

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
    private val keyPendingCleanupKey = stringPreferencesKey("pending_cleanup_key")

    override suspend fun getAccessToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        getToken(dataStore, authSessionCipher, keyAccessToken)

    override suspend fun getRefreshToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        getToken(dataStore, authSessionCipher, keyRefreshToken)

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        LocalAuthDataSourceError,
        > =
        dataStore.readAuthPreference(keyAuthProvider).fold(
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

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, LocalAuthDataSourceError> =
        encryptTokenPair(authSessionCipher, tokens.accessToken, tokens.refreshToken).fold(
            onSuccess = { (encryptedAccessToken, encryptedRefreshToken) ->
                dataStore.writeAuthPreferences { preferences ->
                    preferences[keyAccessToken] = encryptedAccessToken
                    preferences[keyRefreshToken] = encryptedRefreshToken
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, LocalAuthDataSourceError> = encryptTokenPair(
        authSessionCipher,
        session.tokens.accessToken,
        session.tokens.refreshToken,
    ).fold(
        onSuccess = { (encryptedAccessToken, encryptedRefreshToken) ->
            dataStore.writeAuthPreferences { preferences ->
                preferences[keyAccessToken] = encryptedAccessToken
                preferences[keyRefreshToken] = encryptedRefreshToken
                preferences[keyAuthProvider] = session.provider.name
            }
        },
        onFailure = { ResultWithError.Failure(it) },
    )

    override suspend fun clearSession(): ResultWithError<Unit, LocalAuthDataSourceError> =
        dataStore.writeAuthPreferences { preferences ->
            preferences.remove(keyAccessToken)
            preferences.remove(keyRefreshToken)
            preferences.remove(keyAuthProvider)
        }

    override suspend fun setPendingCleanupKey(
        key: UserScopeKey?,
    ): ResultWithError<Unit, LocalAuthDataSourceError> =
        dataStore.writeAuthPreferences { preferences ->
            if (key != null) {
                preferences[keyPendingCleanupKey] = key.key
            } else {
                preferences.remove(keyPendingCleanupKey)
            }
        }

    override suspend fun setPendingCleanupKeyIfAbsent(
        key: UserScopeKey,
    ): ResultWithError<Boolean, LocalAuthDataSourceError> {
        var wasSet = false
        return runCatching {
            dataStore.edit { preferences ->
                if (!preferences.contains(keyPendingCleanupKey)) {
                    preferences[keyPendingCleanupKey] = key.key
                    wasSet = true
                }
            }
        }.fold(
            onSuccess = { ResultWithError.Success(wasSet) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                ResultWithError.Failure(error.toLocalAuthDataSourceError())
            },
        )
    }

    override suspend fun clearPendingCleanupKeyIfMatches(
        key: UserScopeKey,
    ): ResultWithError<Unit, LocalAuthDataSourceError> =
        dataStore.writeAuthPreferences { preferences ->
            if (preferences[keyPendingCleanupKey] == key.key) {
                preferences.remove(keyPendingCleanupKey)
            }
        }

    override suspend fun getPendingCleanupKey(): ResultWithError<
        UserScopeKey?,
        LocalAuthDataSourceError,
        > =
        dataStore.readAuthPreference(keyPendingCleanupKey).fold(
            onSuccess = { keyString ->
                ResultWithError.Success(keyString?.let { UserScopeKey(it) })
            },
            onFailure = { ResultWithError.Failure(it) },
        )
}

private suspend fun DataStore<Preferences>.readAuthPreference(
    key: Preferences.Key<String>,
): ResultWithError<String?, LocalAuthDataSourceError> = runCatching {
    data.first()[key]
}.fold(
    onSuccess = { ResultWithError.Success(it) },
    onFailure = { error ->
        if (error is CancellationException) throw error
        ResultWithError.Failure(error.toLocalAuthDataSourceError())
    },
)

private suspend fun DataStore<Preferences>.writeAuthPreferences(
    update: suspend (MutablePreferences) -> Unit,
): ResultWithError<Unit, LocalAuthDataSourceError> = runCatching {
    edit { preferences -> update(preferences) }
}.fold(
    onSuccess = { ResultWithError.Success(Unit) },
    onFailure = { error ->
        if (error is CancellationException) throw error
        ResultWithError.Failure(error.toLocalAuthDataSourceError())
    },
)

private suspend fun getToken(
    dataStore: DataStore<Preferences>,
    cipher: AuthSessionCipher,
    key: Preferences.Key<String>,
): ResultWithError<String?, LocalAuthDataSourceError> = dataStore.readAuthPreference(key).fold(
    onSuccess = { encodedToken ->
        if (encodedToken == null) {
            ResultWithError.Success(null)
        } else {
            when (val decryptResult = cipher.decrypt(encodedToken).toLocalResult()) {
                is ResultWithError.Success ->
                    ResultWithError.Success<String?, LocalAuthDataSourceError>(decryptResult.data)

                is ResultWithError.Failure ->
                    ResultWithError.Failure<String?, LocalAuthDataSourceError>(decryptResult.error)
            }
        }
    },
    onFailure = { ResultWithError.Failure(it) },
)

private fun encryptTokenPair(
    cipher: AuthSessionCipher,
    accessToken: String,
    refreshToken: String,
): ResultWithError<Pair<String, String>, LocalAuthDataSourceError> =
    cipher.encrypt(accessToken).toLocalResult().fold(
        onSuccess = { encryptedAccessToken ->
            cipher.encrypt(refreshToken).toLocalResult().fold(
                onSuccess = { encryptedRefreshToken ->
                    ResultWithError.Success(encryptedAccessToken to encryptedRefreshToken)
                },
                onFailure = { ResultWithError.Failure(it) },
            )
        },
        onFailure = { ResultWithError.Failure(it) },
    )

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
    fun isStorageFull(throwable: Throwable): Boolean =
        (throwable is ErrnoException && throwable.errno == OsConstants.ENOSPC) ||
            throwable.message.containsAnyIgnoreCase(
                "no space left on device",
                "enospc",
                "disk full",
            )

    fun isReadOnly(throwable: Throwable): Boolean =
        (throwable is ErrnoException && throwable.errno == OsConstants.EROFS) ||
            throwable.message.containsAnyIgnoreCase("read-only file system", "erofs")

    val causes = generateSequence(this) { it.cause }.toList()

    return when {
        causes.any { it is CorruptionException } -> LocalAuthDataSourceError.DataCorrupted
        causes.any(::isStorageFull) -> LocalAuthDataSourceError.StorageFull
        causes.any(::isReadOnly) -> LocalAuthDataSourceError.ReadOnly
        causes.any(::isAccessDeniedThrowable) -> LocalAuthDataSourceError.AccessDenied
        causes.any { it is IOException } -> LocalAuthDataSourceError.TemporarilyUnavailable
        else -> LocalAuthDataSourceError.UnknownError(this)
    }
}

private fun isAccessDeniedThrowable(throwable: Throwable): Boolean =
    throwable is SecurityException ||
        (
            throwable is ErrnoException &&
                (throwable.errno == OsConstants.EACCES || throwable.errno == OsConstants.EPERM)
            ) ||
        throwable.message.containsAnyIgnoreCase(
            "permission denied",
            "operation not permitted",
            "eacces",
            "eperm",
        )

private fun String?.containsAnyIgnoreCase(vararg values: String): Boolean =
    this != null && values.any { contains(it, ignoreCase = true) }
