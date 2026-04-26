package timur.gilfanov.messenger.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshError
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCase
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
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
        authTokenStorage: LocalAuthDataSourceFake,
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
        val authTokenStorage = LocalAuthDataSourceFake().apply {
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
        val authTokenStorage = LocalAuthDataSourceFake()
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
        val authTokenStorage = LocalAuthDataSourceFake()
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
        val authTokenStorage = LocalAuthDataSourceFake().apply {
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
            val authTokenStorage = LocalAuthDataSourceFake().apply {
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
        val authTokenStorage = LocalAuthDataSourceFake().apply {
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
        val authTokenStorage = LocalAuthDataSourceFake().apply {
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

    @Test
    fun `when 401 after completed refresh then triggers new token refresh`() = runTest {
        val authTokenStorage = LocalAuthDataSourceFake().apply {
            accessToken = "old-access-token"
        }
        var refreshInvocationCount = 0
        val engineResponses = ArrayDeque(
            listOf(
                HttpStatusCode.Unauthorized,
                HttpStatusCode.OK,
                HttpStatusCode.Unauthorized,
                HttpStatusCode.OK,
            ),
        )
        val mockEngine = MockEngine {
            val status = engineResponses.removeFirst()
            val body = if (status == HttpStatusCode.OK) "OK" else "Unauthorized"
            respond(body, status)
        }
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                refreshInvocationCount++
                authTokenStorage.saveTokens(newTokens)
                ResultWithError.Success(newTokens)
            },
            scope = this,
        )

        val response1 = client.get("/test")
        val response2 = client.get("/test")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals(2, refreshInvocationCount)
        assertTrue(engineResponses.isEmpty())
    }

    /**
     * Requests that reach the interceptor while a refresh is already in progress must await the
     * same refresh deferred instead of starting their own refresh. The test controls the refresh
     * completion explicitly so request overlap does not depend on wall-clock timing.
     *
     * `mockEngine.config.dispatcher = Dispatchers.Unconfined` is set before constructing the
     * [HttpClient]. Without this, [MockEngine] defaults to [kotlinx.coroutines.Dispatchers.IO],
     * which runs the handler on real IO threads that [advanceUntilIdle] cannot drain. When
     * [releaseRefresh] completes before the IO threads dispatch back, each request finds the
     * deferred inactive and starts its own refresh. [Dispatchers.Unconfined] keeps every handler
     * invocation on the test scheduler, so all 10 request coroutines suspend at
     * `deferred.await()` before the refresh-coroutine runs, making coalescing deterministic.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when concurrent 401 responses then tokenRefreshUseCase is invoked only once`() = runTest {
        val authTokenStorage = LocalAuthDataSourceFake().apply {
            accessToken = "old-access-token"
        }
        var refreshInvocationCount = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val mockEngine = MockEngine {
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        mockEngine.config.dispatcher = Dispatchers.Unconfined
        val client = buildClient(
            mockEngine = mockEngine,
            authTokenStorage = authTokenStorage,
            tokenRefreshUseCase = {
                refreshInvocationCount++
                releaseRefresh.await()
                ResultWithError.Failure(TokenRefreshError.SessionExpired)
            },
            scope = this,
        )

        val jobs = List(10) {
            async { client.get("/test") }
        }

        advanceUntilIdle()
        releaseRefresh.complete(Unit)
        val responses = jobs.awaitAll()

        assertEquals(1, refreshInvocationCount)
        responses.forEach { assertEquals(HttpStatusCode.Unauthorized, it.status) }
    }
}
