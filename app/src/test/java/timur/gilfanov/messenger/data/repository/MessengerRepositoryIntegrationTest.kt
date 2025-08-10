package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithApiError
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithChatDelta
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithDeltas
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithNetworkError
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithSuccessfulChat
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithSuccessfulMessage
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithTimeout
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.toDto
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError

/**
 * Integration tests for MessengerRepository with real remote data sources.
 * Tests end-to-end flows using MockEngine to simulate server responses.
 */
@Category(Component::class)
class MessengerRepositoryIntegrationTest {

    private lateinit var repository: MessengerRepositoryImpl

    private val logger = TestLogger()

    // Test data
    private val testParticipantId =
        ParticipantId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val testChatId = ChatId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"))
    private val testMessageId = MessageId(UUID.fromString("123e4567-e89b-12d3-a456-426614174002"))
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    // Chat List Flow Tests
    @Test
    fun `flowChatList should emit empty list initially`() = runTest {
        // Given
        val mockEngine = MockEngine { respond("No requests expected") }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        repository.flowChatList().test {
            val initialResult = awaitItem()

            // Then
            assertIs<ResultWithError.Success<List<ChatPreview>, *>>(initialResult)
            assertEquals(0, initialResult.data.size)
        }
    }

    @Test
    fun `flowChatList should emit updates when sync receives new data`() = runTest {
        // Given
        val totalDeltas = 3
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("deltas") -> {
                    requestCount++
                    respondWithDeltas(requestCount, totalDeltas)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When - Repository starts sync automatically in init
        repository.flowChatList().test {
            var result = awaitItem() // Can be empty initially or not empty after initial sync
            assertIs<ResultWithError.Success<List<ChatPreview>, *>>(result)
            if (result.data.isEmpty()) {
                result = awaitItem()
                assertIs<ResultWithError.Success<List<ChatPreview>, *>>(result)
            }
            // After ChatUpdatedDelta
            assertEquals(1, result.data.size, "Should have 1 chat after creation")
            assertEquals("Chat Batch 1", result.data.first().name)

            val afterUpdate = awaitItem() // After ChatUpdatedDelta
            assertIs<ResultWithError.Success<List<ChatPreview>, *>>(afterUpdate)
            assertEquals(1, afterUpdate.data.size, "Should still have 1 chat after update")
            assertEquals("Chat Batch 2", afterUpdate.data.first().name)

            val afterDelete = awaitItem() // After ChatDeletedDelta
            assertIs<ResultWithError.Success<List<ChatPreview>, *>>(afterDelete)
            assertEquals(0, afterDelete.data.size, "List should be empty after deletion")
        }

        // Then
        assertEquals(
            totalDeltas,
            requestCount,
            "Expected $totalDeltas delta requests, got $requestCount",
        )
    }

    // Chat Management - Happy Path Tests
    @Test
    fun `createChat should store chat locally when remote call succeeds`() = runTest {
        // Given
        val testChat = createTestChat()
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") && request.method == HttpMethod.Post -> {
                    respondWithSuccessfulChat()
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.createChat(testChat)

        // Then
        assertIs<ResultWithError.Success<Chat, *>>(result)
        assertEquals(testChatId, result.data.id)
        assertEquals("Test Chat", result.data.name)
    }

    @Test
    fun `joinChat should add chat to local storage when join succeeds`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") &&
                    request.url.segments.contains("join") -> {
                    respondWithSuccessfulChat()
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.joinChat(testChatId, inviteLink = "test-invite")

        // Then
        assertIs<ResultWithError.Success<Chat, *>>(result)
        assertEquals(testChatId, result.data.id)
        assertEquals("Test Chat", result.data.name)
    }

    @Test
    fun `deleteChat should remove chat from local storage when remote succeeds`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") && request.method == HttpMethod.Delete -> {
                    // Success response for delete operations
                    val response = ApiResponse(data = Unit, success = true)
                    respond(
                        content = json.encodeToString(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.deleteChat(testChatId)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)
        assertEquals(Unit, result.data)
    }

    @Test
    fun `leaveChat should remove chat from local storage when leave succeeds`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") &&
                    request.url.segments.contains("leave") -> {
                    // Success response for leave operations
                    val response = ApiResponse(data = Unit, success = true)
                    respond(
                        content = json.encodeToString(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.leaveChat(testChatId)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)
        assertEquals(Unit, result.data)
    }

    // Chat Management - Error Handling Tests
    @Test
    fun `createChat should handle API error responses`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") && request.method == HttpMethod.Post -> {
                    respondWithApiError(ApiErrorCode.Unauthorized, "Invalid credentials")
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.createChat(createTestChat())

        // Then
        assertIs<ResultWithError.Failure<*, RepositoryCreateChatError>>(result)
        assertIs<RepositoryCreateChatError.UnknownError>(result.error)
    }

    @Test
    fun `joinChat should handle timeout and map error correctly`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("chats") &&
                    request.url.segments.contains("join") -> {
                    respondWithTimeout()
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.joinChat(testChatId, inviteLink = "test-invite")

        // Then
        assertIs<ResultWithError.Failure<*, RepositoryJoinChatError>>(result)
        assertIs<RepositoryJoinChatError.RemoteUnreachable>(result.error)
    }

    // Message Management - Happy Path Tests
    @Test
    fun `sendMessage should emit progress updates and store message locally`() = runTest {
        // Given
        val testMessage = createTestMessage()

        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("messages") && request.method == HttpMethod.Post -> {
                    respondWithSuccessfulMessage()
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val resultFlow = repository.sendMessage(testMessage)
        val results = resultFlow.toList()

        // Then
        assertTrue(results.isNotEmpty())
        val finalResult = results.last()
        assertIs<ResultWithError.Success<Message, *>>(finalResult)
    }

    @Test
    fun `editMessage should update message locally when remote edit succeeds`() = runTest {
        // Given
        val originalMessage = createTestMessage()
        val editedMessage = originalMessage.copy(text = "Edited message content")

        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("messages") && request.method == HttpMethod.Put -> {
                    // Return the edited message with updated content using existing conversion function
                    val editedMessageDto = editedMessage.toDto()
                    val response = ApiResponse(data = editedMessageDto, success = true)
                    respond(
                        content = json.encodeToString(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When & Then
        repository.editMessage(editedMessage).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, *>>(result)
            assertEquals(editedMessage.id, result.data.id)
            // Verify that the message content was actually updated
            assertIs<TextMessage>(result.data)
            assertEquals("Edited message content", result.data.text)
            awaitComplete()
        }
    }

    @Test
    fun `deleteMessage should remove message locally when remote delete succeeds`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains(
                    "messages",
                ) &&
                    request.method == HttpMethod.Delete -> {
                    // Success response for delete operations
                    val response = ApiResponse(data = Unit, success = true)
                    respond(
                        content = json.encodeToString(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.deleteMessage(testMessageId, DeleteMessageMode.FOR_SENDER_ONLY)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)
        assertEquals(Unit, result.data)
    }

    // Message Management - Error Handling Tests
    @Test
    fun `sendMessage should handle server errors and propagate them correctly`() = runTest {
        // Given
        val testMessage = createTestMessage()
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("messages") && request.method == HttpMethod.Post -> {
                    respondWithApiError(ApiErrorCode.ServerError, "Internal server error")
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When & Then
        repository.sendMessage(testMessage).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<*, RepositorySendMessageError>>(result)
            assertIs<RepositorySendMessageError.RemoteError>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `deleteMessage should handle network errors gracefully`() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            when {
                request.url.segments.contains("messages") &&
                    request.method == HttpMethod.Delete -> {
                    respondWithNetworkError()
                }
                request.url.segments.contains("deltas") -> {
                    respondWithChatDelta(hasMoreChanges = false)
                }
                else -> respond("Unexpected request")
            }
        }
        setupRepositoryWithMockEngine(mockEngine)

        // When
        val result = repository.deleteMessage(testMessageId, DeleteMessageMode.FOR_SENDER_ONLY)

        // Then
        assertIs<ResultWithError.Failure<*, RepositoryDeleteMessageError>>(result)
        assertIs<RepositoryDeleteMessageError.RemoteError>(result.error)
    }

    // Helper functions
    private fun createTestChat(): Chat = Chat(
        id = testChatId,
        participants = persistentSetOf(createTestParticipant()),
        name = "Test Chat",
        pictureUrl = null,
        rules = persistentSetOf(),
        unreadMessagesCount = 0,
        lastReadMessageId = null,
        messages = kotlinx.collections.immutable.persistentListOf(),
    )

    private fun createTestParticipant(): Participant = Participant(
        id = testParticipantId,
        name = "Test User",
        pictureUrl = null,
        joinedAt = testTimestamp,
        onlineAt = testTimestamp,
    )

    private fun createTestMessage(): TextMessage = TextMessage(
        id = testMessageId,
        text = "Test message",
        parentId = null,
        sender = createTestParticipant(),
        recipient = testChatId,
        createdAt = testTimestamp,
        deliveryStatus = null,
    )

    private fun setupRepositoryWithMockEngine(mockEngine: MockEngine) {
        // Recreate local data sources using fake
        val localDataSourceFake = LocalDataSourceFake(logger)
        val localDataSources = LocalDataSources(
            chat = localDataSourceFake,
            message = localDataSourceFake,
            sync = localDataSourceFake,
        )

        // Recreate HTTP client with new mock engine
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        // Recreate remote data sources with new client
        val remoteChatDataSource = RemoteChatDataSourceImpl(httpClient, logger)
        val remoteMessageDataSource = RemoteMessageDataSourceImpl(httpClient, logger)
        val remoteSyncDataSource = RemoteSyncDataSourceImpl(httpClient, logger)

        val remoteDataSources = RemoteDataSources(
            chat = remoteChatDataSource,
            message = remoteMessageDataSource,
            sync = remoteSyncDataSource,
        )

        // Recreate repository with new data sources
        repository = MessengerRepositoryImpl(
            localDataSources = localDataSources,
            remoteDataSources = remoteDataSources,
            logger = logger,
        )
    }
}
