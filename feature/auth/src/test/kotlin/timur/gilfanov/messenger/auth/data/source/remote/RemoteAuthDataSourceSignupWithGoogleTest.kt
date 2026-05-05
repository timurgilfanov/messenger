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
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RemoteAuthDataSourceSignupWithGoogleTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
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

    private fun errorResponse(code: String, message: String = "error"): String =
        json.encodeToString(
            ApiResponse<Unit>(
                data = null,
                success = false,
                error = ApiError(code = code, message = message),
            ),
        )

    @Test
    fun `signupWithGoogle success returns AuthTokens`() = runTest {
        val dataSource = createDataSource(createMockClient(successResponse()))

        val result = dataSource.signupWithGoogle(GoogleIdToken("valid-token"), "Alice")

        assertIs<ResultWithError.Success<AuthTokens, *>>(result)
    }

    @Test
    fun `signupWithGoogle INVALID_TOKEN returns InvalidToken`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("INVALID_TOKEN")))

        val result = dataSource.signupWithGoogle(GoogleIdToken("bad-token"), "Alice")

        assertIs<ResultWithError.Failure<*, SignupWithGoogleRemoteDataSourceError>>(result)
        assertIs<SignupWithGoogleRemoteDataSourceError.InvalidToken>(result.error)
    }

    @Test
    fun `signupWithGoogle ACCOUNT_ALREADY_EXISTS returns AccountAlreadyExists`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("ACCOUNT_ALREADY_EXISTS")))

        val result = dataSource.signupWithGoogle(GoogleIdToken("valid-token"), "Alice")

        assertIs<ResultWithError.Failure<*, SignupWithGoogleRemoteDataSourceError>>(result)
        assertIs<SignupWithGoogleRemoteDataSourceError.AccountAlreadyExists>(result.error)
    }

    @Test
    fun `signupWithGoogle INVALID_NAME returns InvalidName`() = runTest {
        val dataSource =
            createDataSource(createMockClient(errorResponse("INVALID_NAME", "Name is invalid")))

        val result = dataSource.signupWithGoogle(GoogleIdToken("valid-token"), "!!")

        assertIs<ResultWithError.Failure<*, SignupWithGoogleRemoteDataSourceError>>(result)
        assertIs<SignupWithGoogleRemoteDataSourceError.InvalidName>(result.error)
        val invalidName = result.error as SignupWithGoogleRemoteDataSourceError.InvalidName
        assertIs<ProfileNameValidationError.UnknownRuleViolation>(invalidName.reason)
    }

    @Test
    fun `signupWithGoogle unknown error returns RemoteDataSource`() = runTest {
        val dataSource = createDataSource(createMockClient(errorResponse("SERVER_ERROR")))

        val result = dataSource.signupWithGoogle(GoogleIdToken("valid-token"), "Alice")

        assertIs<ResultWithError.Failure<*, SignupWithGoogleRemoteDataSourceError>>(result)
        assertIs<SignupWithGoogleRemoteDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceError.ServerError>(
            (result.error as SignupWithGoogleRemoteDataSourceError.RemoteDataSource).error,
        )
    }

    @Test
    fun `signupWithGoogle null error returns RemoteDataSource`() = runTest {
        val responseBody = json.encodeToString(
            ApiResponse<Unit>(data = null, success = false, error = null),
        )
        val dataSource = createDataSource(createMockClient(responseBody))

        val result = dataSource.signupWithGoogle(GoogleIdToken("valid-token"), "Alice")

        assertIs<ResultWithError.Failure<*, SignupWithGoogleRemoteDataSourceError>>(result)
        assertIs<SignupWithGoogleRemoteDataSourceError.RemoteDataSource>(result.error)
    }
}
