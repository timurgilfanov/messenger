package timur.gilfanov.messenger.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthInterceptorTest {

    private val newTokens = AuthTokens(
        accessToken = "new-access-token",
        refreshToken = "new-refresh-token",
    )

    private fun buildClient(
        mockEngine: MockEngine,
        authTokenStorage: AuthSessionStorageFake,
        tokenRefreshUseCase: TokenRefreshUseCase,
        scope: TestScope,
    ): HttpClient {
        val interceptor = AuthInterceptor(authTokenStorage, tokenRefreshUseCase, scope)
        val client = HttpClient(mockEngine)
        interceptor.install(client)
        return client
    }

    @Test
    fun `when access token is stored then request has Authorization Bearer header`() = runTest {
        val authTokenStorage = AuthSessionStorageFake().apply {
            accessToken = "test-access-token"
        }
        var capturedHeader: String? = null
        val mockEngine = MockEngine { request ->
            capturedHeader = request.headers["Authorization"]
            respond("OK", HttpStatusCode.OK)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = { ResultWithError.Success(newTokens) },
            scope = this,
        )

        client.get("/test")

        assertEquals("Bearer test-access-token", capturedHeader)
    }

    @Test
    fun `when no access token then request has no Authorization header`() = runTest {
        val authTokenStorage = AuthSessionStorageFake()
        var capturedHeader: String? = "should-be-cleared"
        val mockEngine = MockEngine { request ->
            capturedHeader = request.headers["Authorization"]
            respond("OK", HttpStatusCode.OK)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = { ResultWithError.Success(newTokens) },
            scope = this,
        )

        client.get("/test")

        assertNull(capturedHeader)
    }

    @Test
    fun `when response is 200 then no token refresh occurs`() = runTest {
        val authTokenStorage = AuthSessionStorageFake()
        var refreshCallCount = 0
        val mockEngine = MockEngine { respond("OK", HttpStatusCode.OK) }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                refreshCallCount++
                ResultWithError.Success(newTokens)
            },
            scope = this,
        )

        client.get("/test")

        assertEquals(0, refreshCallCount)
    }

    @Test
    fun `when 401 and refresh succeeds then retries request with new access token`() = runTest {
        val authTokenStorage = AuthSessionStorageFake().apply {
            accessToken = "old-access-token"
        }
        val requestHeaders = mutableListOf<String?>()
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            requestHeaders.add(request.headers["Authorization"])
            if (callCount == 1) {
                respond("Unauthorized", HttpStatusCode.Unauthorized)
            } else {
                respond("OK", HttpStatusCode.OK)
            }
        }
        val authRepositoryFake = AuthRepositoryFake(
            AuthSession(
                tokens = AuthTokens("old-access-token", "old-refresh-token"),
                provider = AuthProvider.EMAIL,
            ),
        )
        authRepositoryFake.enqueueRefreshTokenResult(ResultWithError.Success(newTokens))
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                authTokenStorage.saveTokens(newTokens)
                ResultWithError.Success(newTokens)
            },
            scope = this,
        )

        val response = client.get("/test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, callCount)
        assertEquals("Bearer old-access-token", requestHeaders[0])
        assertEquals("Bearer new-access-token", requestHeaders[1])
    }

    @Test
    fun `when 401 and refresh returns SessionExpired then returns original 401 without retry`() =
        runTest {
            val authTokenStorage = AuthSessionStorageFake().apply {
                accessToken = "old-access-token"
            }
            var callCount = 0
            val mockEngine = MockEngine { _ ->
                callCount++
                respond("Unauthorized", HttpStatusCode.Unauthorized)
            }
            val client = buildClient(
                mockEngine = mockEngine,
                authTokenStorage = authTokenStorage,
                tokenRefreshUseCase = {
                    ResultWithError.Failure(TokenRefreshError.SessionExpired)
                },
                scope = this,
            )

            val response = client.get("/test")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(1, callCount)
        }

    @Test
    fun `when 401 and LocalOperationFailed then returns original 401 without retry`() = runTest {
        val authTokenStorage = AuthSessionStorageFake().apply {
            accessToken = "old-access-token"
        }
        var callCount = 0
        val mockEngine = MockEngine {
            callCount++
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                ResultWithError.Failure(
                    TokenRefreshError.LocalOperationFailed(LocalStorageError.StorageFull),
                )
            },
            scope = this,
        )

        val response = client.get("/test")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, callCount)
    }

    @Test
    fun `when 401 and RemoteOperationFailed then returns original 401 without retry`() = runTest {
        val authTokenStorage = AuthSessionStorageFake().apply {
            accessToken = "old-access-token"
        }
        var callCount = 0
        val mockEngine = MockEngine {
            callCount++
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                ResultWithError.Failure(
                    TokenRefreshError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
                )
            },
            scope = this,
        )

        val response = client.get("/test")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, callCount)
    }

    /**
     * [delay] keeps the refresh deferred active long enough for all coroutines to coalesce on it
     * before it completes. Without it, the refresh would finish in the same scheduling turn as the
     * first coroutine, and subsequent coroutines would each start their own refresh.
     *
     * [kotlinx.coroutines.test.StandardTestDispatcher] runs all coroutines on a single thread, so
     * the refresh lock is never actually contended here. This test only verifies the
     * [kotlinx.coroutines.Deferred.isActive] coalescing logic, not the thread-safety of the lock.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when concurrent 401 responses then tokenRefreshUseCase is invoked only once`() = runTest {
        val authTokenStorage = AuthSessionStorageFake().apply {
            accessToken = "old-access-token"
        }
        val refreshInvocationCount = AtomicInteger(0)
        val mockEngine = MockEngine {
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                delay(100)
                refreshInvocationCount.incrementAndGet()
                ResultWithError.Failure(TokenRefreshError.SessionExpired)
            },
            scope = this,
        )

        repeat(10) { launch { client.get("/test") } }
        advanceUntilIdle()

        assertEquals(1, refreshInvocationCount.get())
    }
}
