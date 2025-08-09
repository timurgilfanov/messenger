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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatCreatedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatDeletedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatListDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatMetadataDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatUpdatedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.DeliveryStatusDto
import timur.gilfanov.messenger.data.source.remote.dto.ErrorResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.MessageDto
import timur.gilfanov.messenger.data.source.remote.dto.ParticipantDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.NoOpLogger

@Category(Unit::class)
class RemoteSyncDataSourceImplTest {

    private lateinit var remoteSyncDataSource: RemoteSyncDataSourceImpl
    private lateinit var testLogger: NoOpLogger
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val TEST_PARTICIPANT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        private val TEST_CHAT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        private val TEST_MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174002")
        private val TEST_TIMESTAMP_1 = Instant.parse("2024-01-15T10:30:00Z")
        private val TEST_TIMESTAMP_2 = Instant.parse("2024-01-15T10:31:00Z")
        private val TEST_TIMESTAMP_3 = Instant.parse("2024-01-15T10:32:00Z")
        private val TEST_JOINED_AT = Instant.parse("2024-01-15T10:00:00Z")
        private val TEST_ONLINE_AT = Instant.parse("2024-01-15T11:00:00Z")
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

    private fun createDataSource(mockClient: HttpClient): RemoteSyncDataSourceImpl =
        RemoteSyncDataSourceImpl(mockClient, testLogger)

    private fun createTestDelta(hasMore: Boolean = false): ChatListDeltaDto {
        val participantDto = ParticipantDto(
            id = TEST_PARTICIPANT_ID.toString(),
            name = "Test User",
            pictureUrl = null,
            joinedAt = TEST_JOINED_AT.toString(),
            onlineAt = TEST_ONLINE_AT.toString(),
            isAdmin = false,
            isModerator = false,
        )

        val messageDto = MessageDto(
            id = TEST_MESSAGE_ID.toString(),
            parentId = null,
            sender = participantDto,
            recipient = TEST_CHAT_ID.toString(),
            createdAt = TEST_TIMESTAMP_1.toString(),
            sentAt = TEST_TIMESTAMP_1.toString(),
            deliveredAt = null,
            editedAt = null,
            deliveryStatus = DeliveryStatusDto.Sent,
            content = "Test message",
        )

        val chatMetadataDto = ChatMetadataDto(
            name = "Test Chat",
            participants = listOf(participantDto),
            pictureUrl = null,
            rules = emptyList(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            lastActivityAt = TEST_TIMESTAMP_1.toString(),
        )

        val delta = ChatCreatedDeltaDto(
            chatId = TEST_CHAT_ID.toString(),
            chatMetadata = chatMetadataDto,
            initialMessages = listOf(messageDto),
            timestamp = TEST_TIMESTAMP_1.toString(),
        )

        return ChatListDeltaDto(
            changes = listOf(delta),
            fromTimestamp = null,
            toTimestamp = TEST_TIMESTAMP_1.toString(),
            hasMoreChanges = hasMore,
        )
    }

    @Test
    fun `chatsDeltaUpdates should return success for full sync`() = runTest {
        // Given
        val deltaDto = createTestDelta(hasMore = false)
        val successResponse = ApiResponse(data = deltaDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission before the infinite polling phase
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Success<*, *>>(result)
        val delta = result.data as ChatListDelta
        assertEquals(1, delta.changes.size)
        assertEquals(null, delta.fromTimestamp)
        assertEquals(TEST_TIMESTAMP_1, delta.toTimestamp)
    }

    @Test
    fun `chatsDeltaUpdates should handle incremental sync with timestamp`() = runTest {
        // Given
        val deltaDto = createTestDelta(hasMore = false)
        val modifiedDelta = deltaDto.copy(fromTimestamp = TEST_TIMESTAMP_2.toString())
        val successResponse = ApiResponse(data = modifiedDelta, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission before the infinite polling phase
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = TEST_TIMESTAMP_2)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Success<*, *>>(result)
        val delta = result.data as ChatListDelta
        assertEquals(TEST_TIMESTAMP_2, delta.fromTimestamp)
    }

    @Test
    fun `chatsDeltaUpdates should handle multiple batches when hasMoreChanges is true`() = runTest {
        // Given - Mock multiple responses for pagination
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            val deltaDto = when (callCount) {
                1 -> createTestDelta(hasMore = true)
                2 -> createTestDelta(hasMore = false).copy(
                    toTimestamp = TEST_TIMESTAMP_2.toString(),
                )
                else -> createTestDelta(hasMore = false).copy(changes = emptyList())
            }
            val successResponse = ApiResponse(data = deltaDto, success = true)
            val responseJson = json.encodeToString(successResponse)

            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take first 2 emissions (pagination phase) before infinite polling starts
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val results = resultFlow.take(2).toList()

        // Then
        assertEquals(2, results.size) // Two pagination batches
        results.forEach { result ->
            assertIs<ResultWithError.Success<*, *>>(result)
        }

        // Verify the first batch has hasMoreChanges = true
        val firstDelta = (results[0] as ResultWithError.Success<*, *>).data as ChatListDelta
        assertTrue(firstDelta.hasMoreChanges)

        // Verify the second batch has hasMoreChanges = false (pagination complete)
        val secondDelta = (results[1] as ResultWithError.Success<*, *>).data as ChatListDelta
        assertTrue(!secondDelta.hasMoreChanges)

        assertEquals(2, callCount) // Verify pagination calls made
    }

    @Test
    fun `chatsDeltaUpdates should handle API error response`() = runTest {
        // Given
        val errorResponse = ApiResponse<ChatListDeltaDto>(
            data = null,
            error = ErrorResponseDto(
                code = ApiErrorCode.ServerError,
                message = "Internal server error",
            ),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission (error response terminates the flow)
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `chatsDeltaUpdates should handle SerializationException`() = runTest {
        // Given
        val mockClient = createMockClient("invalid json response")
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission (error response terminates the flow)
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.ServerError ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `chatsDeltaUpdates should handle SocketTimeoutException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission (error response terminates the flow)
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `chatsDeltaUpdates should handle IOException`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            throw IOException("Network connection failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission (error response terminates the flow)
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `chatsDeltaUpdates should handle unexpected Exception`() = runTest {
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
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission (error response terminates the flow)
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(result.error is RemoteDataSourceError.UnknownError)
    }

    @Test
    fun `chatsDeltaUpdates should continue polling after initial backlog complete`() = runTest {
        // Given - Mock engine that returns initial data then later data
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            val deltaDto = when (callCount) {
                1 -> createTestDelta(hasMore = false) // Backlog complete
                else -> createTestDelta(hasMore = false).copy(
                    toTimestamp = TEST_TIMESTAMP_2.toString(),
                ) // Subsequent polls with new data
            }
            val successResponse = ApiResponse(data = deltaDto, success = true)
            val responseJson = json.encodeToString(successResponse)

            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take multiple emissions to verify polling behavior
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        // Take first 3 emissions: backlog + 2 polling results
        val results = resultFlow.take(3).toList()

        // Then - Should have continuous polling behavior
        assertEquals(3, results.size, "Expected exactly 3 results from backlog + 2 polling cycles")
        assertTrue(callCount >= 3, "Expected at least 3 API calls but got $callCount")

        results.forEach { result ->
            assertIs<ResultWithError.Success<*, *>>(result)
        }

        // Verify first emission is the backlog (has data)
        val firstDelta = (results[0] as ResultWithError.Success<*, *>).data as ChatListDelta
        assertTrue(firstDelta.changes.isNotEmpty())
        assertFalse(firstDelta.hasMoreChanges) // Backlog complete
    }

    @Test
    fun `chatsDeltaUpdates should handle various delta types`() = runTest {
        // Given - Mix of Created, Updated, and Deleted deltas
        val participantDto = ParticipantDto(
            id = TEST_PARTICIPANT_ID.toString(),
            name = "Test User",
            pictureUrl = null,
            joinedAt = TEST_JOINED_AT.toString(),
            onlineAt = TEST_ONLINE_AT.toString(),
            isAdmin = false,
            isModerator = false,
        )

        val chatMetadataDto = ChatMetadataDto(
            name = "Test Chat",
            participants = listOf(participantDto),
            pictureUrl = null,
            rules = emptyList(),
            unreadMessagesCount = 1,
            lastReadMessageId = null,
            lastActivityAt = TEST_TIMESTAMP_1.toString(),
        )

        val createdDelta = ChatCreatedDeltaDto(
            chatId = TEST_CHAT_ID.toString(),
            chatMetadata = chatMetadataDto,
            initialMessages = emptyList(),
            timestamp = TEST_TIMESTAMP_1.toString(),
        )

        val updatedDelta = ChatUpdatedDeltaDto(
            chatId = TEST_CHAT_ID.toString(),
            chatMetadata = chatMetadataDto.copy(unreadMessagesCount = 2),
            messagesToAdd = emptyList(),
            messagesToDelete = emptyList(),
            timestamp = TEST_TIMESTAMP_2.toString(),
        )

        val deletedDelta = ChatDeletedDeltaDto(
            chatId = TEST_CHAT_ID.toString(),
            timestamp = TEST_TIMESTAMP_3.toString(),
        )

        val deltaDto = ChatListDeltaDto(
            changes = listOf(createdDelta, updatedDelta, deletedDelta),
            fromTimestamp = null,
            toTimestamp = TEST_TIMESTAMP_3.toString(),
            hasMoreChanges = false,
        )

        val successResponse = ApiResponse(data = deltaDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteSyncDataSource = createDataSource(mockClient)

        // When - Take only the first emission before the infinite polling phase
        val resultFlow = remoteSyncDataSource.chatsDeltaUpdates(since = null)
        val result = resultFlow.first()

        // Then
        assertIs<ResultWithError.Success<*, *>>(result)
        val delta = result.data as ChatListDelta
        assertEquals(3, delta.changes.size)

        // Verify delta types
        assertIs<ChatCreatedDelta>(delta.changes[0])
        assertIs<ChatUpdatedDelta>(delta.changes[1])
        assertIs<ChatDeletedDelta>(delta.changes[2])
    }
}
