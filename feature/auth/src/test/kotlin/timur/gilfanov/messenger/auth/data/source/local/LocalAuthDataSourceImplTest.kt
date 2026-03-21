package timur.gilfanov.messenger.auth.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAuthDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var storage: LocalAuthDataSourceImpl
    private lateinit var cipher: AuthSessionCipherFake
    private lateinit var context: Context
    private val testScope = TestScope(mainDispatcherRule.testDispatcher)

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = {
                context.preferencesDataStoreFile("test_auth_session_${UUID.randomUUID()}")
            },
        )
        cipher = AuthSessionCipherFake()
        storage = LocalAuthDataSourceImpl(dataStore, cipher)
        val clearResult = storage.clearSession()
        assertTrue(
            clearResult is ResultWithError.Success,
            "clearSession should succeed in setUp, but got: ${(clearResult as? ResultWithError.Failure)?.error}",
        )
    }

    @Test
    fun `getAccessToken returns null when storage is empty`() = runTest {
        val result = storage.getAccessToken()

        assertIs<ResultWithError.Success<String?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `getRefreshToken returns null when storage is empty`() = runTest {
        val result = storage.getRefreshToken()

        assertIs<ResultWithError.Success<String?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `getAuthProvider returns null when storage is empty`() = runTest {
        val result = storage.getAuthProvider()

        assertIs<ResultWithError.Success<AuthProvider?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `getAccessToken maps cipher corruption to DataCorrupted`() = runTest {
        cipher.decryptResult = ResultWithError.Failure(AuthSessionCipherError.DataCorrupted)
        dataStore.edit { it[stringPreferencesKey("access_token")] = "encoded" }

        val result = storage.getAccessToken()

        assertEquals(
            ResultWithError.Failure(LocalAuthDataSourceError.DataCorrupted),
            result,
        )
    }

    @Test
    fun `getAuthProvider maps invalid stored provider to DataCorrupted`() = runTest {
        dataStore.edit { it[stringPreferencesKey("auth_provider")] = "BROKEN" }

        val result = storage.getAuthProvider()

        assertEquals(
            ResultWithError.Failure<AuthProvider?, LocalAuthDataSourceError>(
                LocalAuthDataSourceError.DataCorrupted,
            ),
            result,
        )
    }

    @Test
    fun `saveSession maps keystore failure to KeystoreUnavailable`() = runTest {
        cipher.encryptResult = ResultWithError.Failure(AuthSessionCipherError.KeystoreUnavailable)

        val result = storage.saveSession(
            AuthSession(
                tokens = AuthTokens("access", "refresh"),
                provider = AuthProvider.GOOGLE,
            ),
        )

        assertEquals(
            ResultWithError.Failure<Unit, LocalAuthDataSourceError>(
                LocalAuthDataSourceError.KeystoreUnavailable,
            ),
            result,
        )
    }

    @Test
    fun `saveTokens maps access denied to AccessDenied`() = runTest {
        cipher.encryptResult = ResultWithError.Failure(AuthSessionCipherError.AccessDenied)

        val result = storage.saveTokens(AuthTokens("access", "refresh"))

        assertEquals(
            ResultWithError.Failure<Unit, LocalAuthDataSourceError>(
                LocalAuthDataSourceError.AccessDenied,
            ),
            result,
        )
    }

    @Test
    fun `getAccessToken maps DataStore read failure to UnknownError`() = runTest {
        storage = LocalAuthDataSourceImpl(
            DataStoreFake(dataStore = dataStore, readError = true),
            cipher,
        )

        val result = storage.getAccessToken()

        assertIs<ResultWithError.Failure<String?, LocalAuthDataSourceError>>(result)
        val error = assertIs<LocalAuthDataSourceError.UnknownError>(result.error)
        assertIs<IOException>(error.cause)
    }

    @Test
    fun `saveSession maps DataStore write failure to UnknownError`() = runTest {
        storage = LocalAuthDataSourceImpl(
            DataStoreFake(dataStore = dataStore, writeError = true),
            cipher,
        )

        val result = storage.saveSession(
            AuthSession(
                tokens = AuthTokens("access", "refresh"),
                provider = AuthProvider.EMAIL,
            ),
        )

        assertIs<ResultWithError.Failure<Unit, LocalAuthDataSourceError>>(result)
        val error = assertIs<LocalAuthDataSourceError.UnknownError>(result.error)
        assertIs<IOException>(error.cause)
    }
}

private class AuthSessionCipherFake : AuthSessionCipher {
    var encryptResult: ResultWithError<String, AuthSessionCipherError>? = null
    var decryptResult: ResultWithError<String, AuthSessionCipherError>? = null

    override fun encrypt(plaintext: String): ResultWithError<String, AuthSessionCipherError> =
        encryptResult ?: ResultWithError.Success("encrypted:$plaintext")

    override fun decrypt(encoded: String): ResultWithError<String, AuthSessionCipherError> =
        decryptResult ?: ResultWithError.Success(encoded.removePrefix("encrypted:"))
}

private class DataStoreFake(
    private val dataStore: DataStore<Preferences>,
    private val readError: Boolean = false,
    private val writeError: Boolean = false,
) : DataStore<Preferences> {

    override val data: Flow<Preferences>
        get() = dataStore.data.map {
            if (readError) {
                throw IOException("Read data fake exception")
            }
            it
        }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (writeError) throw IOException("Write data fake exception")
        return dataStore.updateData(transform)
    }
}
