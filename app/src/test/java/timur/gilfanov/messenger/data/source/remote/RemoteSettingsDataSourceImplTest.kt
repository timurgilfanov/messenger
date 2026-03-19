package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ErrorResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingItemDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingSyncResultDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingsResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.SyncSettingsResponseDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger

@Category(Unit::class)
class RemoteSettingsDataSourceImplTest {

    private lateinit var dataSource: RemoteSettingsDataSourceImpl
    private lateinit var testLogger: NoOpLogger

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z")
    }

    @Before
    fun setup() {
        testLogger = NoOpLogger()
    }

    private fun createMockClient(
        responseBody: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient {
        val mockEngine = MockEngine { request ->
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

    private fun createDataSource(mockClient: HttpClient): RemoteSettingsDataSourceImpl =
        RemoteSettingsDataSourceImpl(mockClient, testLogger)

    // GET settings tests
    @Test
    fun `get should return success with parsed RemoteSettings`() = runTest {
        // Given
        val settingsResponse = SettingsResponseDto(
            settings = listOf(
                SettingItemDto(key = "ui_language", value = "English", version = 1),
            ),
        )
        val response = ApiResponse(data = settingsResponse, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Success<RemoteSettings, *>>(result)
        val uiLanguage = result.data.uiLanguage
        assertIs<RemoteSetting.Valid<UiLanguage>>(uiLanguage)
        assertEquals(UiLanguage.English, uiLanguage.value)
        assertEquals(1, uiLanguage.serverVersion)
    }

    @Test
    fun `get should handle API error response`() = runTest {
        // Given
        val response = ApiResponse<SettingsResponseDto>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.RateLimitExceeded,
                message = "Error message",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError0 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError0)
        assertIs<RemoteDataSourceError.RateLimitExceeded>(remoteDataSourceError0.error)
    }

    @Test
    fun `get should handle ServerError response`() = runTest {
        // Given
        val response = ApiResponse<SettingsResponseDto>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.ServerError,
                message = "Internal server error",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError1 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError1)
        assertIs<RemoteDataSourceError.ServerError>(remoteDataSourceError1.error)
    }

    @Test
    fun `get should handle Unknown error code`() = runTest {
        // Given
        val response = ApiResponse<SettingsResponseDto>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.Unknown("CUSTOM_ERROR"),
                message = "Unknown error",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError2 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError2)
        assertIs<RemoteDataSourceError.UnknownServiceError>(remoteDataSourceError2.error)
    }

    @Test
    fun `get should handle SocketTimeoutException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError3 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError3)
        assertIs<RemoteDataSourceError.ServiceUnavailable.Timeout>(remoteDataSourceError3.error)
    }

    @Test
    fun `get should handle UnknownHostException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw UnknownHostException("Host not found")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError4 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError4)
        assertIs<RemoteDataSourceError.ServiceUnavailable.NetworkNotAvailable>(
            remoteDataSourceError4.error,
        )
    }

    @Test
    fun `get should handle IOException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw IOException("Network error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError5 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError5)
        assertIs<RemoteDataSourceError.ServiceUnavailable.ServerUnreachable>(
            remoteDataSourceError5.error,
        )
    }

    @Test
    fun `get should handle SerializationException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError6 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError6)
        assertIs<RemoteDataSourceError.ServerError>(remoteDataSourceError6.error)
    }

    // changeUiLanguage tests
    @Test
    fun `changeUiLanguage should return success`() = runTest {
        // Given
        val response = ApiResponse(data = Unit, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.changeUiLanguage(UiLanguage.German)

        // Then
        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `changeUiLanguage should handle error response`() = runTest {
        // Given
        val response = ApiResponse<kotlin.Unit>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.RateLimitExceeded,
                message = "Error message",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.changeUiLanguage(UiLanguage.German)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError7 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError7)
        assertIs<RemoteDataSourceError.RateLimitExceeded>(remoteDataSourceError7.error)
    }

    @Test
    fun `changeUiLanguage should handle ServerError response`() = runTest {
        // Given
        val response = ApiResponse<kotlin.Unit>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.ServerError,
                message = "Internal server error",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.changeUiLanguage(UiLanguage.German)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError8 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError8)
        assertIs<RemoteDataSourceError.ServerError>(remoteDataSourceError8.error)
    }

    @Test
    fun `changeUiLanguage should handle network error`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw IOException("Network error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.changeUiLanguage(UiLanguage.German)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
    }

    // put tests
    @Test
    fun `put should return success`() = runTest {
        // Given
        val response = ApiResponse(data = Unit, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)
        val settings = Settings(uiLanguage = UiLanguage.German)

        // When
        val result = dataSource.put(settings)

        // Then
        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `put should handle error response`() = runTest {
        // Given
        val response = ApiResponse<kotlin.Unit>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.RateLimitExceeded,
                message = "Error message",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)
        val settings = Settings(uiLanguage = UiLanguage.German)

        // When
        val result = dataSource.put(settings)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError9 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError9)
        assertIs<RemoteDataSourceError.RateLimitExceeded>(remoteDataSourceError9.error)
    }

    // syncSingleSetting tests
    @Test
    fun `syncSingleSetting should return Success result`() = runTest {
        // Given
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto.Success(
                    key = "ui_language",
                    newVersion = 2,
                ),
            ),
        )
        val response = ApiResponse(data = syncResponse, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val request = TypedSettingSyncRequest.UiLanguage(
            request = SettingSyncRequest(
                value = UiLanguage.German,
                clientVersion = 1,
                lastKnownServerVersion = 0,
                modifiedAt = TEST_TIMESTAMP,
            ),
        )

        // When
        val result = dataSource.syncSingleSetting(request)

        // Then
        assertIs<ResultWithError.Success<SyncResult, *>>(result)
        val syncSuccess = result.data
        assertIs<SyncResult.Success>(syncSuccess)
        assertEquals(2, syncSuccess.newVersion)
    }

    @Test
    fun `syncSingleSetting should return Conflict result`() = runTest {
        // Given
        val serverModifiedAt = TEST_TIMESTAMP
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto.Conflict(
                    key = "ui_language",
                    newVersion = 3,
                    serverValue = "German",
                    serverVersion = 2,
                    serverModifiedAt = serverModifiedAt,
                ),
            ),
        )
        val response = ApiResponse(data = syncResponse, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val request = TypedSettingSyncRequest.UiLanguage(
            request = SettingSyncRequest(
                value = UiLanguage.English,
                clientVersion = 1,
                lastKnownServerVersion = 1,
                modifiedAt = TEST_TIMESTAMP,
            ),
        )

        // When
        val result = dataSource.syncSingleSetting(request)

        // Then
        assertIs<ResultWithError.Success<SyncResult, *>>(result)
        val conflict = result.data
        assertIs<SyncResult.Conflict>(conflict)
        assertEquals("German", conflict.serverValue)
        assertEquals(2, conflict.serverVersion)
        assertEquals(3, conflict.newVersion)
    }

    @Test
    fun `syncSingleSetting should handle missing result in response`() = runTest {
        // Given - Response with empty results
        val syncResponse = SyncSettingsResponseDto(results = emptyList())
        val response = ApiResponse(data = syncResponse, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val request = TypedSettingSyncRequest.UiLanguage(
            request = SettingSyncRequest(
                value = UiLanguage.German,
                clientVersion = 1,
                lastKnownServerVersion = 0,
                modifiedAt = TEST_TIMESTAMP,
            ),
        )

        // When
        val result = dataSource.syncSingleSetting(request)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError10 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError10)
        val unknownServiceError = remoteDataSourceError10.error
        assertIs<RemoteDataSourceError.UnknownServiceError>(unknownServiceError)
        assertEquals(
            "Missing sync result for key: ${SettingKey.UI_LANGUAGE.key}",
            unknownServiceError.reason.value,
        )
    }

    // syncBatch tests
    @Test
    fun `syncBatch should return map of results`() = runTest {
        // Given
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto.Success(
                    key = "ui_language",
                    newVersion = 2,
                ),
            ),
        )
        val response = ApiResponse(data = syncResponse, success = true)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val requests = listOf(
            TypedSettingSyncRequest.UiLanguage(
                request = SettingSyncRequest(
                    value = UiLanguage.German,
                    clientVersion = 1,
                    lastKnownServerVersion = 0,
                    modifiedAt = TEST_TIMESTAMP,
                ),
            ),
        )

        // When
        val result = dataSource.syncBatch(requests)

        // Then
        assertIs<ResultWithError.Success<Map<String, SyncResult>, *>>(result)
        assertEquals(1, result.data.size)
        assertIs<SyncResult.Success>(result.data["ui_language"])
    }

    @Test
    fun `syncBatch should handle error response`() = runTest {
        // Given
        val response = ApiResponse<SyncSettingsResponseDto>(
            data = null,
            success = false,
            error = ErrorResponseDto(
                code = ApiErrorCode.RateLimitExceeded,
                message = "Error message",
            ),
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val requests = listOf(
            TypedSettingSyncRequest.UiLanguage(
                request = SettingSyncRequest(
                    value = UiLanguage.German,
                    clientVersion = 1,
                    lastKnownServerVersion = 0,
                    modifiedAt = TEST_TIMESTAMP,
                ),
            ),
        )

        // When
        val result = dataSource.syncBatch(requests)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError11 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError11)
        assertIs<RemoteDataSourceError.RateLimitExceeded>(remoteDataSourceError11.error)
    }

    @Test
    fun `syncBatch should handle network timeout`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        val requests = listOf(
            TypedSettingSyncRequest.UiLanguage(
                request = SettingSyncRequest(
                    value = UiLanguage.German,
                    clientVersion = 1,
                    lastKnownServerVersion = 0,
                    modifiedAt = TEST_TIMESTAMP,
                ),
            ),
        )

        // When
        val result = dataSource.syncBatch(requests)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError12 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError12)
        assertIs<RemoteDataSourceError.ServiceUnavailable.Timeout>(remoteDataSourceError12.error)
    }

    // Unexpected exception test
    @Test
    fun `get should handle unexpected RuntimeException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("Unexpected error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        val remoteDataSourceError13 = result.error
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(remoteDataSourceError13)
        assertIs<RemoteDataSourceError.UnknownServiceError>(remoteDataSourceError13.error)
    }
}
