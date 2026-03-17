package timur.gilfanov.messenger.auth.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

@Singleton
class AuthSessionStorageImpl @Inject constructor(@ApplicationContext private val context: Context) :
    AuthSessionStorage {

    companion object {
        private const val FILE_NAME = "auth_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun getAccessToken(): ResultWithError<String?, AuthSessionStorageError> =
        runCatching { prefs.getString(KEY_ACCESS_TOKEN, null) }.toResult()

    override suspend fun getRefreshToken(): ResultWithError<String?, AuthSessionStorageError> =
        runCatching { prefs.getString(KEY_REFRESH_TOKEN, null) }.toResult()

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        AuthSessionStorageError,
        > =
        runCatching {
            prefs.getString(KEY_AUTH_PROVIDER, null)?.let {
                runCatching { AuthProvider.valueOf(it) }.getOrNull()
            }
        }.toResult()

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, AuthSessionStorageError> = runCatching {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .apply()
    }.toResult()

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, AuthSessionStorageError> = runCatching {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.tokens.refreshToken)
            .putString(KEY_AUTH_PROVIDER, session.provider.name)
            .apply()
    }.toResult()

    override suspend fun clearSession(): ResultWithError<Unit, AuthSessionStorageError> =
        runCatching {
            prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_AUTH_PROVIDER)
                .apply()
        }.toResult()

    private fun <T> Result<T>.toResult(): ResultWithError<T, AuthSessionStorageError> = fold(
        onSuccess = { ResultWithError.Success(it) },
        onFailure = { e ->
            val error = when (e) {
                is SecurityException -> AuthSessionStorageError.AccessDenied
                is GeneralSecurityException -> AuthSessionStorageError.DataCorrupted
                is IOException -> AuthSessionStorageError.UnknownError(e)
                else -> AuthSessionStorageError.UnknownError(e)
            }
            ResultWithError.Failure(error)
        },
    )
}
