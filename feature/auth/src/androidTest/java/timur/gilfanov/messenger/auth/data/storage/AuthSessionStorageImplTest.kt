package timur.gilfanov.messenger.auth.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

@RunWith(AndroidJUnit4::class)
@FeatureTest
class AuthSessionStorageImplTest {

    companion object {
        private var storage: AuthSessionStorageImpl? = null

        private fun getStorage(): AuthSessionStorageImpl {
            if (storage == null) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                storage = AuthSessionStorageImpl(context)
            }
            return storage!!
        }
    }

    @Before
    fun setUp() {
        runBlocking {
            val clearResult = getStorage().clearSession()
            assertTrue(
                clearResult is ResultWithError.Success,
                "clearSession should succeed in setUp, but got: ${(clearResult as? ResultWithError.Failure)?.error}",
            )
        }
    }

    // ── saveSession ──────────────────────────────────────────────────
    @Test
    fun saveSession_stores_access_token() = runTest {
        // Given
        val tokens = AuthTokens("access-token-1", "refresh-token-1")
        val session = AuthSession(tokens, AuthProvider.EMAIL)

        // When
        val saveResult = getStorage().saveSession(session)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveResult)

        val getResult = getStorage().getAccessToken()

        // Then
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getResult)
        assertEquals("access-token-1", getResult.data)
    }

    @Test
    fun saveSession_stores_refresh_token() = runTest {
        // Given
        val tokens = AuthTokens("access-token-1", "refresh-token-1")
        val session = AuthSession(tokens, AuthProvider.EMAIL)

        // When
        val saveResult = getStorage().saveSession(session)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveResult)

        val getResult = getStorage().getRefreshToken()

        // Then
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getResult)
        assertEquals("refresh-token-1", getResult.data)
    }

    @Test
    fun saveSession_stores_auth_provider() = runTest {
        // Given
        val tokens = AuthTokens("access-token-1", "refresh-token-1")
        val session = AuthSession(tokens, AuthProvider.GOOGLE)

        // When
        val saveResult = getStorage().saveSession(session)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveResult)

        val getResult = getStorage().getAuthProvider()

        // Then
        assertIs<ResultWithError.Success<AuthProvider?, AuthSessionStorageError>>(getResult)
        assertEquals(AuthProvider.GOOGLE, getResult.data)
    }

    // ── saveTokens ───────────────────────────────────────────────────
    @Test
    fun saveTokens_updates_access_and_refresh_tokens() = runTest {
        // Given
        val initialTokens = AuthTokens("access-token-1", "refresh-token-1")
        val initialSession = AuthSession(initialTokens, AuthProvider.EMAIL)
        val saveSessionResult = getStorage().saveSession(initialSession)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveSessionResult)

        // When
        val newTokens = AuthTokens("access-token-2", "refresh-token-2")
        val saveResult = getStorage().saveTokens(newTokens)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveResult)

        val getAccessResult = getStorage().getAccessToken()
        val getRefreshResult = getStorage().getRefreshToken()

        // Then
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getAccessResult)
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getRefreshResult)
        assertEquals("access-token-2", getAccessResult.data)
        assertEquals("refresh-token-2", getRefreshResult.data)
    }

    @Test
    fun saveTokens_does_not_update_auth_provider() = runTest {
        // Given
        val initialTokens = AuthTokens("access-token-1", "refresh-token-1")
        val initialSession = AuthSession(initialTokens, AuthProvider.EMAIL)
        val saveSessionResult = getStorage().saveSession(initialSession)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveSessionResult)

        // When
        val newTokens = AuthTokens("access-token-2", "refresh-token-2")
        val saveTokensResult = getStorage().saveTokens(newTokens)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveTokensResult)
        val getProviderResult = getStorage().getAuthProvider()

        // Then
        assertIs<ResultWithError.Success<AuthProvider?, AuthSessionStorageError>>(getProviderResult)
        assertEquals(AuthProvider.EMAIL, getProviderResult.data)
    }

    // ── clearSession ─────────────────────────────────────────────────
    @Test
    fun clearSession_removes_all_stored_data() = runTest {
        // Given
        val tokens = AuthTokens("access-token-1", "refresh-token-1")
        val session = AuthSession(tokens, AuthProvider.EMAIL)
        val saveResult = getStorage().saveSession(session)
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(saveResult)

        // When
        val clearResult = getStorage().clearSession()
        val getAccessResult = getStorage().getAccessToken()
        val getRefreshResult = getStorage().getRefreshToken()
        val getProviderResult = getStorage().getAuthProvider()

        // Then
        assertIs<ResultWithError.Success<Unit, AuthSessionStorageError>>(clearResult)
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getAccessResult)
        assertIs<ResultWithError.Success<String?, AuthSessionStorageError>>(getRefreshResult)
        assertIs<ResultWithError.Success<AuthProvider?, AuthSessionStorageError>>(getProviderResult)
        assertNull(getAccessResult.data)
        assertNull(getRefreshResult.data)
        assertNull(getProviderResult.data)
    }
}
