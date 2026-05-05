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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.domain.entity.ResultWithError
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

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private fun createDataSource(mockClient: HttpClient): RemoteAuthDataSourceImpl =
        RemoteAuthDataSourceImpl(mockClient, testLogger)

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
