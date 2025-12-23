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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.SettingItemDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingSyncResultDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingsResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.SyncSettingsResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.SyncStatusDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

@Category(Unit::class)
class RemoteSettingsDataSourceImplTest {

    private lateinit var dataSource: RemoteSettingsDataSourceImpl
    private lateinit var testLogger: NoOpLogger
    private lateinit var testIdentity: Identity

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        private val TEST_DEVICE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        private val TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z")
    }

    @Before
    fun setup() {
        testLogger = NoOpLogger()
        testIdentity = Identity(
            userId = UserId(TEST_USER_ID),
            deviceId = DeviceId(TEST_DEVICE_ID),
        )
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
        val result = dataSource.get(testIdentity)

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
        )
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
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
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.ServiceUnavailable.Timeout>(result.error.error)
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
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable>(result.error.error)
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
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable>(result.error.error)
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
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.ServerError>(result.error.error)
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
        val result = dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        // Then
        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `changeUiLanguage should handle error response`() = runTest {
        // Given
        val response = ApiResponse<kotlin.Unit>(data = null, success = false)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        // When
        val result = dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
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
        val result = dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

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
        val result = dataSource.put(testIdentity, settings)

        // Then
        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `put should handle error response`() = runTest {
        // Given
        val response = ApiResponse<kotlin.Unit>(data = null, success = false)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)
        val settings = Settings(uiLanguage = UiLanguage.German)

        // When
        val result = dataSource.put(testIdentity, settings)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
    }

    // syncSingleSetting tests
    @Test
    fun `syncSingleSetting should return Success result`() = runTest {
        // Given
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto(
                    key = "ui_language",
                    status = SyncStatusDto.SUCCESS,
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
                identity = testIdentity,
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
        assertIs<SyncResult.Success>(result.data)
        assertEquals(2, result.data.newVersion)
    }

    @Test
    fun `syncSingleSetting should return Conflict result`() = runTest {
        // Given
        val serverModifiedAt = TEST_TIMESTAMP.toString()
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto(
                    key = "ui_language",
                    status = SyncStatusDto.CONFLICT,
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
                identity = testIdentity,
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
        assertIs<SyncResult.Conflict>(result.data)
        val conflict = result.data
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
                identity = testIdentity,
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
    }

    // syncBatch tests
    @Test
    fun `syncBatch should return map of results`() = runTest {
        // Given
        val syncResponse = SyncSettingsResponseDto(
            results = listOf(
                SettingSyncResultDto(
                    key = "ui_language",
                    status = SyncStatusDto.SUCCESS,
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
                    identity = testIdentity,
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
        val response = ApiResponse<SyncSettingsResponseDto>(data = null, success = false)
        val responseJson = json.encodeToString(response)

        val mockClient = createMockClient(responseJson)
        dataSource = createDataSource(mockClient)

        val requests = listOf(
            TypedSettingSyncRequest.UiLanguage(
                request = SettingSyncRequest(
                    identity = testIdentity,
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
                    identity = testIdentity,
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
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.ServiceUnavailable.Timeout>(result.error.error)
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
        val result = dataSource.get(testIdentity)

        // Then
        assertIs<ResultWithError.Failure<*, RemoteSettingsDataSourceError>>(result)
        assertIs<RemoteSettingsDataSourceError.RemoteDataSource>(result.error)
        assertIs<RemoteDataSourceErrorV2.UnknownServiceError>(result.error.error)
    }
}
