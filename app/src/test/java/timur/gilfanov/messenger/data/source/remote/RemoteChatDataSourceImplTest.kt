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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatDto
import timur.gilfanov.messenger.data.source.remote.dto.ErrorResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.ParticipantDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.util.NoOpLogger

@Category(Unit::class)
class RemoteChatDataSourceImplTest {

    private lateinit var remoteChatDataSource: RemoteChatDataSourceImpl
    private lateinit var testChat: Chat
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
        private val TEST_JOINED_AT = Instant.parse("2024-01-15T10:30:00Z")
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

        testChat = Chat(
            id = ChatId(TEST_CHAT_ID),
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(testParticipant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
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

    private fun createDataSource(mockClient: HttpClient): RemoteChatDataSourceImpl =
        RemoteChatDataSourceImpl(mockClient, testLogger)

    @Test
    fun `createChat should return success when API responds successfully`() = runTest {
        // Given
        val expectedChatDto = ChatDto(
            id = testChat.id.id.toString(),
            name = testChat.name,
            pictureUrl = testChat.pictureUrl,
            participants = listOf(
                ParticipantDto(
                    id = testParticipant.id.id.toString(),
                    name = testParticipant.name,
                    pictureUrl = testParticipant.pictureUrl,
                    joinedAt = testParticipant.joinedAt.toString(),
                    onlineAt = testParticipant.onlineAt?.toString(),
                ),
            ),
        )
        val successResponse = ApiResponse(data = expectedChatDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Success<Chat, RemoteDataSourceError>>(result)
        assertEquals(testChat.id, result.data.id)
        assertEquals(testChat.name, result.data.name)
    }

    @Test
    fun `createChat should return error when API responds with error`() = runTest {
        // Given
        val errorResponse = ApiResponse<ChatDto>(
            data = null,
            error = ErrorResponseDto(code = ApiErrorCode.ChatClosed, message = "Chat is closed"),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ChatClosed>(result.error)
    }

    @Test
    fun `createChat should handle network exceptions`() = runTest {
        // Given
        val mockClient = createMockClient("", HttpStatusCode.InternalServerError)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // The empty response causes JSON parsing to fail, which results in UnknownError wrapping the exception
        assertTrue(
            result.error is RemoteDataSourceError.UnknownError ||
                result.error is RemoteDataSourceError.ServerError,
        )
    }

    @Test
    fun `createChat should handle SerializationException`() = runTest {
        // Given - invalid JSON response that causes parsing failure
        val mockClient = createMockClient("invalid json response")
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // Invalid JSON parsing failures result in UnknownError through the generic exception handler
        assertTrue(
            result.error is RemoteDataSourceError.UnknownError ||
                result.error is RemoteDataSourceError.ServerError,
        )
    }

    @Test
    fun `createChat should handle SocketTimeoutException`() = runTest {
        // Given - MockEngine that throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `createChat should handle IOException`() = runTest {
        // Given - MockEngine that throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network connection failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // IOException is mapped by ErrorMapper.mapException()
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `createChat should handle unexpected Exception`() = runTest {
        // Given - MockEngine that throws unexpected RuntimeException
        val mockEngine = MockEngine { request ->
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("Unexpected error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // Generic exceptions are mapped by ErrorMapper.mapException()
        assertTrue(result.error is RemoteDataSourceError.UnknownError)
    }

    @Test
    fun `deleteChat should return success when API responds successfully`() = runTest {
        // Given
        val successResponse = ApiResponse<Unit>(success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)
    }

    @Test
    fun `deleteChat should return error when chat not found`() = runTest {
        // Given
        val errorResponse = ApiResponse<Unit>(
            error = ErrorResponseDto(code = ApiErrorCode.ChatNotFound, message = "Chat not found"),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ChatNotFound>(result.error)
    }

    @Test
    fun `joinChat should return success with chat data`() = runTest {
        // Given
        val expectedChatDto = ChatDto(
            id = testChat.id.id.toString(),
            name = testChat.name,
            pictureUrl = testChat.pictureUrl,
            participants = listOf(
                ParticipantDto(
                    id = testParticipant.id.id.toString(),
                    name = testParticipant.name,
                    pictureUrl = testParticipant.pictureUrl,
                    joinedAt = testParticipant.joinedAt.toString(),
                    onlineAt = testParticipant.onlineAt?.toString(),
                ),
            ),
        )
        val successResponse = ApiResponse(data = expectedChatDto, success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invite-link")

        // Then
        assertIs<ResultWithError.Success<Chat, RemoteDataSourceError>>(result)
        assertEquals(testChat.id, result.data.id)
        assertEquals(testChat.name, result.data.name)
    }

    @Test
    fun `joinChat should handle invalid invite link error`() = runTest {
        // Given
        val errorResponse = ApiResponse<ChatDto>(
            error = ErrorResponseDto(
                code = ApiErrorCode.InvalidInviteLink,
                message = "Invalid invite link",
            ),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invalid-link")

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.InvalidInviteLink>(result.error)
    }

    @Test
    fun `leaveChat should return success when API responds successfully`() = runTest {
        // Given
        val successResponse = ApiResponse<Unit>(success = true)
        val responseJson = json.encodeToString(successResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)
    }

    @Test
    fun `leaveChat should handle already left error`() = runTest {
        // Given
        val errorResponse = ApiResponse<Unit>(
            error = ErrorResponseDto(
                code = ApiErrorCode.ChatNotFound,
                message = "User not in chat",
            ),
            success = false,
        )
        val responseJson = json.encodeToString(errorResponse)

        val mockClient = createMockClient(responseJson)
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ChatNotFound>(result.error)
    }

    @Test
    fun `deleteChat should handle SerializationException`() = runTest {
        // Given - invalid JSON response that causes parsing failure
        val mockClient = createMockClient("invalid json response")
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // Invalid JSON parsing failures result in UnknownError through the generic exception handler
        assertTrue(
            result.error is RemoteDataSourceError.UnknownError ||
                result.error is RemoteDataSourceError.ServerError,
        )
    }

    @Test
    fun `deleteChat should handle SocketTimeoutException`() = runTest {
        // Given - MockEngine that throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `joinChat should handle SerializationException`() = runTest {
        // Given - invalid JSON response that causes parsing failure
        val mockClient = createMockClient("invalid json response")
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invite-link")

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        // Invalid JSON parsing failures result in UnknownError through the generic exception handler
        assertTrue(
            result.error is RemoteDataSourceError.UnknownError ||
                result.error is RemoteDataSourceError.ServerError,
        )
    }

    @Test
    fun `joinChat should handle IOException`() = runTest {
        // Given - MockEngine that throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network connection failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invite-link")

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `leaveChat should handle SocketTimeoutException`() = runTest {
        // Given - MockEngine that throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Request timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `leaveChat should handle unexpected Exception`() = runTest {
        // Given - MockEngine that throws unexpected RuntimeException
        val mockEngine = MockEngine { request ->
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("Unexpected error")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertTrue(result.error is RemoteDataSourceError.UnknownError)
    }

    // Direct exception tests to ensure specific catch blocks are hit
    @Test
    fun `createChat should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `deleteChat should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `deleteChat should handle direct IOException`() = runTest {
        // Given - MockEngine that directly throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network I/O failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }

    @Test
    fun `joinChat should handle direct SocketTimeoutException`() = runTest {
        // Given - MockEngine that directly throws SocketTimeoutException
        val mockEngine = MockEngine { request ->
            throw SocketTimeoutException("Direct timeout", null)
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invite-link")

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerUnreachable>(result.error)
    }

    @Test
    fun `joinChat should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.joinChat(testChat.id, "invite-link")

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `leaveChat should handle direct SerializationException`() = runTest {
        // Given - MockEngine that directly throws SerializationException
        val mockEngine = MockEngine { request ->
            throw SerializationException("Serialization failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertIs<RemoteDataSourceError.ServerError>(result.error)
    }

    @Test
    fun `leaveChat should handle direct IOException`() = runTest {
        // Given - MockEngine that directly throws IOException
        val mockEngine = MockEngine { request ->
            throw IOException("Network I/O failed")
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        remoteChatDataSource = createDataSource(mockClient)

        // When
        val result = remoteChatDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Any, RemoteDataSourceError>>(result)
        assertTrue(
            result.error is RemoteDataSourceError.NetworkNotAvailable ||
                result.error is RemoteDataSourceError.ServerUnreachable ||
                result.error is RemoteDataSourceError.UnknownError,
        )
    }
}
