package timur.gilfanov.messenger.auth.data.source.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAuthDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var storage: LocalAuthDataSourceImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LocalAuthDataSourceImpl(context)
        runBlocking {
            val clearResult = storage.clearSession()
            assertTrue(
                clearResult is ResultWithError.Success,
                "clearSession should succeed in setUp, but got: ${(clearResult as? ResultWithError.Failure)?.error}",
            )
        }
    }

    @Test
    fun `getAccessToken returns null when storage is empty`() = runBlocking {
        // When
        val result = storage.getAccessToken()

        // Then
        assertIs<ResultWithError.Success<String?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `getRefreshToken returns null when storage is empty`() = runBlocking {
        // When
        val result = storage.getRefreshToken()

        // Then
        assertIs<ResultWithError.Success<String?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `getAuthProvider returns null when storage is empty`() = runBlocking {
        // When
        val result = storage.getAuthProvider()

        // Then
        assertIs<ResultWithError.Success<AuthProvider?, LocalAuthDataSourceError>>(result)
        assertNull(result.data)
    }

    // NOTE: Tests for saveSession, saveTokens, and clearSession require encryption via Android
    // Keystore. Robolectric 4.15.1 does not properly support Android Keystore operations
    // (returns KeystoreUnavailable error). These operations work on real devices/emulators.
    // Full integration testing should be performed via connected device tests or emulator.
}
