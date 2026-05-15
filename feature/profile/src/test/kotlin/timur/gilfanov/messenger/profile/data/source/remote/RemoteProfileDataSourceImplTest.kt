package timur.gilfanov.messenger.profile.data.source.remote

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
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.testutil.NoOpLogger

@Category(Unit::class)
class RemoteProfileDataSourceImplTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `getCurrentProfile returns profile from profile endpoint`() = runTest {
        val response = json.encodeToString(
            ApiResponse(
                data = ProfileDto("Timur", "https://example.com/avatar.jpg"),
                success = true,
            ),
        )
        val dataSource = RemoteProfileDataSourceImpl(createMockClient(response), NoOpLogger())

        val result = dataSource.getCurrentProfile()

        val success = assertIs<ResultWithError.Success<Profile, *>>(result)
        assertEquals(Profile("Timur", "https://example.com/avatar.jpg"), success.data)
    }

    @Test
    fun `getCurrentProfile maps TOKEN_EXPIRED to authentication error`() = runTest {
        val dataSource = RemoteProfileDataSourceImpl(
            createMockClient(errorResponse("TOKEN_EXPIRED")),
            NoOpLogger(),
        )

        val result = dataSource.getCurrentProfile()

        val failure = assertIs<ResultWithError.Failure<*, RemoteProfileDataSourceError>>(result)
        assertIs<RemoteProfileDataSourceError.Authentication.TokenExpired>(failure.error)
    }

    @Test
    fun `getCurrentProfile maps rate limit to remote data source error`() = runTest {
        val dataSource = RemoteProfileDataSourceImpl(
            createMockClient(errorResponse("RATE_LIMIT_EXCEEDED")),
            NoOpLogger(),
        )

        val result = dataSource.getCurrentProfile()

        val failure = assertIs<ResultWithError.Failure<*, RemoteProfileDataSourceError>>(result)
        val error = assertIs<RemoteProfileDataSourceError.RemoteDataSource>(failure.error)
        assertIs<RemoteDataSourceError.RateLimitExceeded>(error.error)
    }

    @Test
    fun `getCurrentProfile maps missing success data to unknown service error`() = runTest {
        val response = json.encodeToString(ApiResponse<ProfileDto>(data = null, success = true))
        val dataSource = RemoteProfileDataSourceImpl(createMockClient(response), NoOpLogger())

        val result = dataSource.getCurrentProfile()

        val failure = assertIs<ResultWithError.Failure<*, RemoteProfileDataSourceError>>(result)
        val error = assertIs<RemoteProfileDataSourceError.RemoteDataSource>(failure.error)
        assertIs<RemoteDataSourceError.UnknownServiceError>(error.error)
    }

    @Test
    fun `getCurrentProfile maps malformed data to unknown service error`() = runTest {
        val dataSource = RemoteProfileDataSourceImpl(createMockClient("{"), NoOpLogger())

        val result = dataSource.getCurrentProfile()

        val failure = assertIs<ResultWithError.Failure<*, RemoteProfileDataSourceError>>(result)
        val error = assertIs<RemoteProfileDataSourceError.RemoteDataSource>(failure.error)
        assertIs<RemoteDataSourceError.UnknownServiceError>(error.error)
    }

    private fun createMockClient(
        responseBody: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            assertEquals(ProfileApiRoutes.PROFILE, request.url.encodedPath)
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

    private fun errorResponse(code: String, message: String = "error"): String =
        json.encodeToString(
            ApiResponse<Unit>(
                data = null,
                success = false,
                error = ApiError(code = code, message = message),
            ),
        )
}
