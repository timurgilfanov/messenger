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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.DeliveryStatusDto
import timur.gilfanov.messenger.data.source.remote.dto.ErrorResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.MessageDto
import timur.gilfanov.messenger.data.source.remote.dto.ParticipantDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

@Category(Unit::class)
class RemoteMessageDataSourceImplTest {

    private lateinit var remoteMessageDataSource: RemoteMessageDataSourceImpl
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant
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
        private val TEST_CREATED_AT = Instant.parse("2024-01-15T10:30:00Z")
        private val TEST_JOINED_AT = Instant.parse("2024-01-15T10:00:00Z")
        private val TEST_ONLINE_AT = Instant.parse("2024-01-15T11:00:00Z")
    }

    @Before
    fun setup() {
        testLogger = NoOpLogger()

        testParticipant = Participant(
            id = ParticipantId(TEST_PARTICIPANT_ID),
            name = "Test User",
            pictureUrl = null,
            joinedAt = TEST_JOINED_AT,
            onlineAt = TEST_ONLINE_AT,
        )

        testMessage = TextMessage(
            id = MessageId(TEST_MESSAGE_ID),
            parentId = null,
            sender = testParticipant,
            recipient = ChatId(TEST_CHAT_ID),
            createdAt = TEST_CREATED_AT,
            sentAt = null,
            deliveredAt = null,
            editedAt = null,
            deliveryStatus = null,
            text = "Test message content",
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

    private fun createDataSource(mockClient: HttpClient): RemoteMessageDataSourceImpl =
        RemoteMessageDataSourceImpl(mockClient, testLogger)

    @Test
    fun `sendMessage should return success when API responds successfully`() = runTest {
        // Given
        val expectedMessageDto = MessageDto(
            id = testMessage.id.id.toString(),
            parentId = testMessage.parentId?.id?.toString(),
            sender = ParticipantDto(
                id = testParticipant.id.id.toString(),
                name = testParticipant.name,
                pictureUrl = testParticipant.pictureUrl,
                joinedAt = testParticipant.joinedAt.toString(),
                onlineAt = testParticipant.onlineAt?.toString(),
            ),
            recipient = testMessage.recipient.id.toString(),
            createdAt = testMessage.createdAt.toString(),
            sentAt = TEST_CREATED_AT.toString(),
            deliveredAt = null,
            editedAt = testMessage.editedAt?.toString(),
            deliveryStatus = DeliveryStatusDto.Sent,
            content = testMessage.text,
        )
        val successResponse = ApiResponse(data = expectedMessageDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission after HTTP request
        val result = results.first()
        assertIs<ResultWithError.Success<*, *>>(result)
        val resultMessage = result.data as TextMessage
        assertEquals(testMessage.id, resultMessage.id)
        assertEquals(testMessage.text, resultMessage.text)
    }

    @Test
    fun `sendMessage should return error when API responds with error`() = runTest {
        // Given
        val errorResponse = ApiResponse<MessageDto>(
            data = null,
            error = ErrorResponseDto(
                code = ApiErrorCode.MessageNotFound,
                message = "Message not found",
            ),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.MessageNotFound>(result.error)
    }

    @Test
    fun `editMessage should return success when API responds successfully`() = runTest {
        // Given
        val editedMessage = testMessage.copy(
            text = "Edited message content",
            editedAt = TEST_CREATED_AT,
        )
        val expectedMessageDto = MessageDto(
            id = editedMessage.id.id.toString(),
            parentId = editedMessage.parentId?.id?.toString(),
            sender = ParticipantDto(
                id = testParticipant.id.id.toString(),
                name = testParticipant.name,
                pictureUrl = testParticipant.pictureUrl,
                joinedAt = testParticipant.joinedAt.toString(),
                onlineAt = testParticipant.onlineAt?.toString(),
            ),
            recipient = editedMessage.recipient.id.toString(),
            createdAt = editedMessage.createdAt.toString(),
            sentAt = editedMessage.sentAt?.toString(),
            deliveredAt = editedMessage.deliveredAt?.toString(),
            editedAt = editedMessage.editedAt?.toString(),
            deliveryStatus = DeliveryStatusDto.Sent,
            content = editedMessage.text,
        )
        val successResponse = ApiResponse(data = expectedMessageDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.editMessage(editedMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertIs<ResultWithError.Success<*, *>>(result)
        val resultMessage = result.data as TextMessage
        assertEquals(editedMessage.id, resultMessage.id)
        assertEquals(editedMessage.text, resultMessage.text)
    }

    @Test
    fun `deleteMessage should return success when API responds successfully`() = runTest {
        // Given
        val successResponse = ApiResponse<Unit>(success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)
    }

    @Test
    fun `deleteMessage should handle message not found error`() = runTest {
        // Given
        val errorResponse = ApiResponse<Unit>(
            error = ErrorResponseDto(
                code = ApiErrorCode.MessageNotFound,
                message = "Message not found",
            ),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.MessageNotFound>(result.error)
    }

    @Test
    fun `sendMessage should handle SerializationException`() = runTest {
        // Given - invalid JSON response that causes parsing failure
        val mockClient = createMockClient("invalid json response")
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.UnknownError ||
                result.error is RemoteDataSourceError.ServerError,
        )
    }

    @Test
    fun `sendMessage should handle SocketTimeoutException`() = runTest {
        // Given - MockEngine that throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `editMessage should handle IOException`() = runTest {
        // Given - MockEngine that throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network connection failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.editMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `deleteMessage should handle unexpected Exception`() = runTest {
        // Given - MockEngine that throws unexpected RuntimeException
        val mockEngine = MockEngine { request ->
            @Suppress("TooGenericExceptionThrown") // Intentionally testing generic exception
            throw RuntimeException("Unexpected error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(result.error is RemoteDataSourceError.UnknownError)
    }

    // Direct exception tests to ensure specific catch blocks are hit
    @Test
    fun `sendMessage should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `sendMessage should handle direct IOException`() = runTest {
        // Given - MockEngine that directly throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network I/O failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size) // Single emission
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `editMessage should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.editMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `editMessage should handle direct SocketTimeoutException`() = runTest {
        // Given - MockEngine that directly throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.editMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `editMessage should handle direct unexpected Exception`() = runTest {
        // Given - MockEngine that directly throws RuntimeException
        val mockEngine = MockEngine { request ->
            @Suppress("TooGenericExceptionThrown") // Intentionally testing generic exception
            throw RuntimeException("Unexpected error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val resultFlow = remoteMessageDataSource.editMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(result.error is RemoteDataSourceError.UnknownError)
    }

    @Test
    fun `deleteMessage should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `deleteMessage should handle direct SocketTimeoutException`() = runTest {
        // Given - MockEngine that directly throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `deleteMessage should handle direct IOException`() = runTest {
        // Given - MockEngine that directly throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network I/O failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteMessageDataSource = createDataSource(mockClient)

        // When
        val result = remoteMessageDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }
}
