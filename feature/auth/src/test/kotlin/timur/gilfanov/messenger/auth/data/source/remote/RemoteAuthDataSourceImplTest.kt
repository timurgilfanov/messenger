package timur.gilfanov.messenger.auth.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.data.source.remote.dto.AuthTokensDto
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RemoteAuthDataSourceImplTest {

    private lateinit var dataSource: RemoteAuthDataSourceImpl
    private lateinit var testLogger: NoOpLogger

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Before
    fun setup() {
        testLogger = NoOpLogger()
    }

    private fun createMockClient(
        responseBody: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        mockEngine.config.dispatcher = Dispatchers.Unconfined

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private fun createDataSource(mockClient: HttpClient): RemoteAuthDataSourceImpl =
        RemoteAuthDataSourceImpl(mockClient, testLogger)

    private fun tokensResponse(): String = json.encodeToString(
        ApiResponse(
            data = AuthTokensDto(
                accessToken = "access-token",
                refreshToken = "refresh-token",
            ),
            success = true,
            error = null,
        ),
    )

    private fun errorResponse(
        code: String,
        message: String = "error",
        details: Map<String, String>? = null,
    ): String = json.encodeToString(
        ApiResponse<Unit>(
            data = null,
            success = false,
            error = ApiError(code = code, message = message, details = details),
        ),
    )

    @Test
    fun `loginWithCredentials success returns AuthTokens`() = runTest {
        dataSource = createDataSource(createMockClient(tokensResponse()))

        val result = dataSource.loginWithCredentials(mockCredentials())

        assertIs<ResultWithError.Success<AuthTokens, LoginWithCredentialsRemoteDataSourceError>>(
            result,
        )
    }

    @Test
    fun `loginWithCredentials INVALID_CREDENTIALS returns InvalidCredentials`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("INVALID_CREDENTIALS")))

        val result = dataSource.loginWithCredentials(mockCredentials())

        assertIs<ResultWithError.Failure<*, LoginWithCredentialsRemoteDataSourceError>>(result)
        assertIs<LoginWithCredentialsRemoteDataSourceError.InvalidCredentials>(result.error)
    }

    @Test
    fun `loginWithCredentials EMAIL_NOT_VERIFIED returns EmailNotVerified`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("EMAIL_NOT_VERIFIED")))

        val result = dataSource.loginWithCredentials(mockCredentials())

        assertIs<ResultWithError.Failure<*, LoginWithCredentialsRemoteDataSourceError>>(result)
        assertIs<LoginWithCredentialsRemoteDataSourceError.EmailNotVerified>(result.error)
    }

    @Test
    fun `loginWithCredentials ACCOUNT_SUSPENDED returns AccountSuspended`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("ACCOUNT_SUSPENDED")))

        val result = dataSource.loginWithCredentials(mockCredentials())

        assertIs<ResultWithError.Failure<*, LoginWithCredentialsRemoteDataSourceError>>(result)
        assertIs<LoginWithCredentialsRemoteDataSourceError.AccountSuspended>(result.error)
    }

    @Test
    fun `loginWithCredentials SERVER_ERROR returns RemoteDataSource ServerError`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("SERVER_ERROR")))

        val result = dataSource.loginWithCredentials(mockCredentials())

        assertIs<ResultWithError.Failure<*, LoginWithCredentialsRemoteDataSourceError>>(result)
        val error = assertIs<LoginWithCredentialsRemoteDataSourceError.RemoteDataSource>(
            result.error,
        )
        assertIs<RemoteDataSourceError.ServerError>(error.error)
    }

    @Test
    fun `loginWithGoogle success returns AuthTokens`() = runTest {
        dataSource = createDataSource(createMockClient(tokensResponse()))

        val result = dataSource.loginWithGoogle(
            timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken("google-id-token"),
        )

        assertIs<ResultWithError.Success<AuthTokens, LoginWithGoogleRemoteDataSourceError>>(
            result,
        )
    }

    @Test
    fun `loginWithGoogle INVALID_TOKEN returns InvalidToken`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("INVALID_TOKEN")))

        val result = dataSource.loginWithGoogle(
            timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken("bad-token"),
        )

        assertIs<ResultWithError.Failure<*, LoginWithGoogleRemoteDataSourceError>>(result)
        assertIs<LoginWithGoogleRemoteDataSourceError.InvalidToken>(result.error)
    }

    @Test
    fun `loginWithGoogle ACCOUNT_NOT_FOUND returns AccountNotFound`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("ACCOUNT_NOT_FOUND")))

        val result = dataSource.loginWithGoogle(
            timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken("google-id-token"),
        )

        assertIs<ResultWithError.Failure<*, LoginWithGoogleRemoteDataSourceError>>(result)
        assertIs<LoginWithGoogleRemoteDataSourceError.AccountNotFound>(result.error)
    }

    @Test
    fun `loginWithGoogle ACCOUNT_SUSPENDED returns AccountSuspended`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("ACCOUNT_SUSPENDED")))

        val result = dataSource.loginWithGoogle(
            timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken("google-id-token"),
        )

        assertIs<ResultWithError.Failure<*, LoginWithGoogleRemoteDataSourceError>>(result)
        assertIs<LoginWithGoogleRemoteDataSourceError.AccountSuspended>(result.error)
    }

    @Test
    fun `refresh success returns AuthTokens`() = runTest {
        dataSource = createDataSource(createMockClient(tokensResponse()))

        val result = dataSource.refresh("refresh-token")

        assertIs<ResultWithError.Success<AuthTokens, RefreshRemoteDataSourceError>>(result)
    }

    @Test
    fun `refresh TOKEN_EXPIRED returns TokenExpired`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("TOKEN_EXPIRED")))

        val result = dataSource.refresh("refresh-token")

        assertIs<ResultWithError.Failure<*, RefreshRemoteDataSourceError>>(result)
        assertIs<RefreshRemoteDataSourceError.TokenExpired>(result.error)
    }

    @Test
    fun `refresh SESSION_REVOKED returns SessionRevoked`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("SESSION_REVOKED")))

        val result = dataSource.refresh("refresh-token")

        assertIs<ResultWithError.Failure<*, RefreshRemoteDataSourceError>>(result)
        assertIs<RefreshRemoteDataSourceError.SessionRevoked>(result.error)
    }

    @Test
    fun `refresh COOLDOWN_ACTIVE with remaining_ms returns CooldownActive`() = runTest {
        dataSource = createDataSource(
            createMockClient(
                errorResponse(
                    code = "COOLDOWN_ACTIVE",
                    details = mapOf("remaining_ms" to "250"),
                ),
            ),
        )

        val result = dataSource.refresh("refresh-token")

        assertIs<ResultWithError.Failure<*, RefreshRemoteDataSourceError>>(result)
        val error = assertIs<RefreshRemoteDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceError.CooldownActive>(error.error)
    }

    @Test
    fun `logout success returns Unit`() = runTest {
        dataSource = createDataSource(
            createMockClient(
                json.encodeToString(
                    ApiResponse<Unit>(data = null, success = true, error = null),
                ),
            ),
        )

        val result = dataSource.logout("access-token")

        assertIs<ResultWithError.Success<Unit, LogoutRemoteDataSourceError>>(result)
    }

    @Test
    fun `logout SERVER_ERROR returns RemoteDataSource ServerError`() = runTest {
        dataSource = createDataSource(createMockClient(errorResponse("SERVER_ERROR")))

        val result = dataSource.logout("access-token")

        assertIs<ResultWithError.Failure<Unit, LogoutRemoteDataSourceError>>(result)
        val error = assertIs<LogoutRemoteDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceError.ServerError>(error.error)
    }

    @Test
    fun `register PASSWORD_TOO_SHORT with min_length returns PasswordTooShort with value`() =
        runTest {
            // Given
            val response = ApiResponse<Unit>(
                data = null,
                success = false,
                error = ApiError(
                    code = "PASSWORD_TOO_SHORT",
                    message = "Password is too short",
                    details = mapOf("min_length" to "8"),
                ),
            )
            val responseJson = json
                .encodeToString(response)

            val mockClient = createMockClient(responseJson)
            dataSource = createDataSource(mockClient)

            // When
            val result = dataSource.register(
                credentials = mockCredentials(),
                name = "Test User",
            )

            // Then
            assertIs<ResultWithError.Failure<*, RegisterRemoteDataSourceError>>(result)
            assertIs<RegisterRemoteDataSourceError.InvalidPassword>(result.error)
            val passwordError = result.error as RegisterRemoteDataSourceError.InvalidPassword
            assertIs<PasswordValidationError.PasswordTooShort>(passwordError.reason)
            assertEquals(8, passwordError.reason.minLength)
        }

    @Test
    fun `register PASSWORD_TOO_SHORT without details returns PasswordTooShort with null`() =
        runTest {
            // Given
            val response = ApiResponse<Unit>(
                data = null,
                success = false,
                error = ApiError(
                    code = "PASSWORD_TOO_SHORT",
                    message = "Password is too short",
                ),
            )
            val responseJson = json
                .encodeToString(response)

            val mockClient = createMockClient(responseJson)
            dataSource = createDataSource(mockClient)

            // When
            val result = dataSource.register(
                credentials = mockCredentials(),
                name = "Test User",
            )

            // Then
            assertIs<ResultWithError.Failure<*, RegisterRemoteDataSourceError>>(result)
            assertIs<RegisterRemoteDataSourceError.InvalidPassword>(result.error)
            val passwordError = result.error as RegisterRemoteDataSourceError.InvalidPassword
            assertIs<PasswordValidationError.PasswordTooShort>(passwordError.reason)
            assertEquals(null, passwordError.reason.minLength)
        }

    @Test
    fun `register PASSWORD_TOO_SHORT without min_length key returns null`() = runTest {
        // Given
        val response = ApiResponse<Unit>(
            data = null,
            success = false,
            error = ApiError(
                code = "PASSWORD_TOO_SHORT",
                message = "Password is too short",
                details = mapOf("other_key" to "value"),
            ),
        )
        val responseJson = json
            .encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.register(
            credentials = mockCredentials(),
            name = "Test User",
        )

        // Then
        assertIs<ResultWithError.Failure<*, RegisterRemoteDataSourceError>>(result)
        assertIs<RegisterRemoteDataSourceError.InvalidPassword>(result.error)
        val passwordError = result.error as RegisterRemoteDataSourceError.InvalidPassword
        assertIs<PasswordValidationError.PasswordTooShort>(passwordError.reason)
        assertEquals(null, passwordError.reason.minLength)
    }

    @Test
    fun `register PASSWORD_TOO_LONG with max_length returns PasswordTooLong with value`() =
        runTest {
            // Given
            val response = ApiResponse<Unit>(
                data = null,
                success = false,
                error = ApiError(
                    code = "PASSWORD_TOO_LONG",
                    message = "Password is too long",
                    details = mapOf("max_length" to "128"),
                ),
            )
            val responseJson = json
                .encodeToString(response)

            val mockClient = createMockClient(responseJson)
            dataSource = createDataSource(mockClient)

            // When
            val result = dataSource.register(
                credentials = mockCredentials(),
                name = "Test User",
            )

            // Then
            assertIs<ResultWithError.Failure<*, RegisterRemoteDataSourceError>>(result)
            assertIs<RegisterRemoteDataSourceError.InvalidPassword>(result.error)
            val passwordError = result.error as RegisterRemoteDataSourceError.InvalidPassword
            assertIs<PasswordValidationError.PasswordTooLong>(passwordError.reason)
            assertEquals(128, passwordError.reason.maxLength)
        }

    @Test
    fun `register PASSWORD_TOO_LONG without details returns PasswordTooLong with null`() = runTest {
        // Given
        val response = ApiResponse<Unit>(
            data = null,
            success = false,
            error = ApiError(
                code = "PASSWORD_TOO_LONG",
                message = "Password is too long",
            ),
        )
        val responseJson = json
            .encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.register(
            credentials = mockCredentials(),
            name = "Test User",
        )

        // Then
        assertIs<ResultWithError.Failure<*, RegisterRemoteDataSourceError>>(result)
        assertIs<RegisterRemoteDataSourceError.InvalidPassword>(result.error)
        val passwordError = result.error as RegisterRemoteDataSourceError.InvalidPassword
        assertIs<PasswordValidationError.PasswordTooLong>(passwordError.reason)
        assertEquals(null, passwordError.reason.maxLength)
    }

    private fun mockCredentials() = timur.gilfanov.messenger.domain.entity.auth.Credentials(
        email = timur.gilfanov.messenger.domain.entity.auth.Email("test@example.com"),
        password = timur.gilfanov.messenger.domain.entity.auth.Password("password123"),
    )
}
