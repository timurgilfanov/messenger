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
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailUnknownError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RemoteAuthDataSourceRegisterTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val credentials = Credentials(Email("user@example.com"), Password("secret123"))
    private val name = "Alice"

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
        RemoteAuthDataSourceImpl(mockClient, NoOpLogger())

    private fun successResponse(): String = json.encodeToString(
        ApiResponse(
            data = timur.gilfanov.messenger.auth.data.source.remote.dto.AuthTokensDto(
                accessToken = "access",
                refreshToken = "refresh",
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
    fun `register success returns AuthTokens`() = runTest {
        val dataSource = createDataSource(createMockClient(successResponse()))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Success<AuthTokens, *>>(result)
    }

    @Test
    fun `register EMAIL_TAKEN returns InvalidEmail with EmailTaken`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("EMAIL_TAKEN")))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Failure<*, RegisterError>>(result)
        val error = assertIs<RegisterError.InvalidEmail>(result.error)
        assertIs<SignupEmailError.EmailTaken>(error.reason)
    }

    @Test
    fun `register EMAIL_NOT_EXISTS returns InvalidEmail with EmailUnknownError`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("EMAIL_NOT_EXISTS")))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Failure<*, RegisterError>>(result)
        val error = assertIs<RegisterError.InvalidEmail>(result.error)
        assertIs<EmailUnknownError>(error.reason)
    }

    @Test
    fun `register PASSWORD_TOO_SHORT with min_length returns PasswordTooShort with minLength`() =
        runTest {
            val dataSource = createDataSource(
                createMockClient(
                    errorResponse(
                        "PASSWORD_TOO_SHORT",
                        details = mapOf("min_length" to "8"),
                    ),
                ),
            )

            val result = dataSource.register(credentials, name)

            assertIs<ResultWithError.Failure<*, RegisterError>>(result)
            val error = assertIs<RegisterError.InvalidPassword>(result.error)
            val passwordError = assertIs<PasswordValidationError.PasswordTooShort>(error.reason)
            kotlin.test.assertEquals(8, passwordError.minLength)
        }

    @Test
    fun `register PASSWORD_TOO_SHORT without min_length returns PasswordTooShort with null`() =
        runTest {
            val dataSource =
                createDataSource(createMockClient(errorResponse("PASSWORD_TOO_SHORT")))

            val result = dataSource.register(credentials, name)

            assertIs<ResultWithError.Failure<*, RegisterError>>(result)
            val error = assertIs<RegisterError.InvalidPassword>(result.error)
            val passwordError = assertIs<PasswordValidationError.PasswordTooShort>(error.reason)
            kotlin.test.assertNull(passwordError.minLength)
        }

    @Test
    fun `register PASSWORD_TOO_LONG with max_length returns PasswordTooLong with maxLength`() =
        runTest {
            val dataSource = createDataSource(
                createMockClient(
                    errorResponse(
                        "PASSWORD_TOO_LONG",
                        details = mapOf("max_length" to "64"),
                    ),
                ),
            )

            val result = dataSource.register(credentials, name)

            assertIs<ResultWithError.Failure<*, RegisterError>>(result)
            val error = assertIs<RegisterError.InvalidPassword>(result.error)
            val passwordError = assertIs<PasswordValidationError.PasswordTooLong>(error.reason)
            kotlin.test.assertEquals(64, passwordError.maxLength)
        }

    @Test
    fun `register PASSWORD_TOO_LONG without max_length detail returns PasswordTooLong with null`() =
        runTest {
            val dataSource =
                createDataSource(createMockClient(errorResponse("PASSWORD_TOO_LONG")))

            val result = dataSource.register(credentials, name)

            assertIs<ResultWithError.Failure<*, RegisterError>>(result)
            val error = assertIs<RegisterError.InvalidPassword>(result.error)
            val passwordError = assertIs<PasswordValidationError.PasswordTooLong>(error.reason)
            kotlin.test.assertNull(passwordError.maxLength)
        }

    @Test
    fun `register INVALID_NAME returns InvalidName with UnknownRuleViolation`() = runTest {
        val dataSource =
            createDataSource(createMockClient(errorResponse("INVALID_NAME", "Name is invalid")))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Failure<*, RegisterError>>(result)
        val error = assertIs<RegisterError.InvalidName>(result.error)
        assertIs<ProfileNameValidationError.UnknownRuleViolation>(error.reason)
    }

    @Test
    fun `register unknown error code returns RemoteDataSource`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("SERVER_ERROR")))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Failure<*, RegisterError>>(result)
        assertIs<RegisterError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceError.ServerError>(
            (result.error as RegisterError.RemoteDataSource).error,
        )
    }

    @Test
    fun `register null error returns RemoteDataSource`() = runTest {
        val responseBody = json.encodeToString(
            ApiResponse<Unit>(data = null, success = false, error = null),
        )
        val dataSource = createDataSource(createMockClient(responseBody))

        val result = dataSource.register(credentials, name)

        assertIs<ResultWithError.Failure<*, RegisterError>>(result)
        assertIs<RegisterError.RemoteDataSource>(result.error)
    }
}
