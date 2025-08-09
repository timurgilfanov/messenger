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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
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
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.util.NoOpLogger

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
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
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
    fun `sendMessage should emit progress updates and return success`() = runTest {
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
        assertTrue(results.size >= 11) // 10 progress updates + final result

        // Check progress updates
        val progressUpdates = results.dropLast(1)
        progressUpdates.forEachIndexed { index, result ->
            assertIs<ResultWithError.Success<*, *>>(result)
            val message = result.data as TextMessage
            val expectedProgress = ((index + 1) * 100) / 10
            assertIs<DeliveryStatus.Sending>(message.deliveryStatus)
            assertEquals(expectedProgress, message.deliveryStatus.progress)
        }

        // Check final result
        val finalResult = results.last()
        assertIs<ResultWithError.Success<*, *>>(finalResult)
        val finalMessage = finalResult.data as TextMessage
        assertEquals(testMessage.id, finalMessage.id)
        assertEquals(testMessage.text, finalMessage.text)
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

        // Then - should have progress updates followed by error
        assertTrue(results.size >= 11)
        val finalResult = results.last()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(finalResult)
        assertIs<RemoteDataSourceError.MessageNotFound>(finalResult.error)
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

        // Then - should have progress updates followed by error
        assertTrue(results.size >= 11)
        val finalResult = results.last()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(finalResult)
        assertTrue(
            finalResult.error is RemoteDataSourceError.UnknownError ||
                finalResult.error is RemoteDataSourceError.ServerError,
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

        // Then - should have progress updates followed by timeout error
        assertTrue(results.size >= 11)
        val finalResult = results.last()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(finalResult)
        assertIs<RemoteDataSourceError.ServerUnreachable>(finalResult.error)
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

        // Then - should have progress updates followed by error
        assertTrue(results.size >= 11)
        val finalResult = results.last()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(finalResult)
        assertIs<RemoteDataSourceError.ServerError>(finalResult.error)
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

        // Then - should have progress updates followed by network error
        assertTrue(results.size >= 11)
        val finalResult = results.last()
        assertIs<ResultWithError.Failure<*, RemoteDataSourceError>>(finalResult)
        assertTrue(
            finalResult.error is RemoteDataSourceError.NetworkNotAvailable ||
                finalResult.error is RemoteDataSourceError.ServerUnreachable ||
                finalResult.error is RemoteDataSourceError.UnknownError,
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
