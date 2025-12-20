package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.ConnectException
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatCreatedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatDeletedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatListDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatMetadataDto
import timur.gilfanov.messenger.data.source.remote.dto.ChatUpdatedDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.DeliveryStatusDto
import timur.gilfanov.messenger.data.source.remote.dto.ErrorResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.MessageDto
import timur.gilfanov.messenger.data.source.remote.dto.ParticipantDto

/**
 * Mock server scenarios for integration testing remote data sources.
 * Provides predefined responses for common test scenarios.
 */
object MockServerScenarios {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    // Test data constants
    private val TEST_PARTICIPANT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val TEST_CHAT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
    private val TEST_MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174002")
    private val TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z")
    private val TEST_JOINED_AT = Instant.parse("2024-01-15T10:00:00Z")
    private val TEST_ONLINE_AT = Instant.parse("2024-01-15T11:00:00Z")

    // Success response scenarios
    suspend fun MockRequestHandleScope.respondWithSuccessfulChat(
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        val participant = createTestParticipant()
        val message = createTestMessage(participant)
        val chat = ChatDto(
            id = TEST_CHAT_ID.toString(),
            participants = listOf(participant),
            name = "Test Chat",
            pictureUrl = null,
            rules = emptyList(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = listOf(message),
        )

        val response = ApiResponse(data = chat, success = true)
        return respond(
            content = json.encodeToString(response),
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    suspend fun MockRequestHandleScope.respondWithSuccessfulMessage(
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        val participant = createTestParticipant()
        val message = createTestMessage(participant)
        val response = ApiResponse(data = message, success = true)

        return respond(
            content = json.encodeToString(response),
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    suspend fun MockRequestHandleScope.respondWithChatDelta(
        hasMoreChanges: Boolean = false,
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        val participant = createTestParticipant()
        val message = createTestMessage(participant)
        val metadata = ChatMetadataDto(
            name = "Test Chat",
            participants = listOf(participant),
            pictureUrl = null,
            rules = emptyList(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            lastActivityAt = TEST_TIMESTAMP.toString(),
        )

        val delta = ChatCreatedDeltaDto(
            chatId = TEST_CHAT_ID.toString(),
            chatMetadata = metadata,
            initialMessages = listOf(message),
            timestamp = TEST_TIMESTAMP.toString(),
        )

        val deltaList = ChatListDeltaDto(
            changes = listOf(delta),
            fromTimestamp = null,
            toTimestamp = TEST_TIMESTAMP.toString(),
            hasMoreChanges = hasMoreChanges,
        )

        val response = ApiResponse(data = deltaList, success = true)
        return respond(
            content = json.encodeToString(response),
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    // Error response scenarios
    suspend fun MockRequestHandleScope.respondWithApiError(
        errorCode: ApiErrorCode,
        message: String = "API error",
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        val errorResponse = ApiResponse<Unit>(
            data = null,
            error = ErrorResponseDto(
                code = errorCode,
                message = message,
            ),
            success = false,
        )

        return respond(
            content = json.encodeToString(errorResponse),
            status = HttpStatusCode.BadRequest,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    suspend fun MockRequestHandleScope.respondWithServerError(
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        return respondError(
            status = HttpStatusCode.InternalServerError,
            content = "Internal Server Error",
        )
    }

    suspend fun MockRequestHandleScope.respondWithTimeout(delayMs: Long = 100): Nothing {
        delay(delayMs)
        throw SocketTimeoutException("Request timeout", null)
    }

    suspend fun MockRequestHandleScope.respondWithNetworkError(delayMs: Long = 0): Nothing {
        if (delayMs > 0) delay(delayMs)
        throw ConnectException("Network connection failed")
    }

    suspend fun MockRequestHandleScope.respondWithInvalidJson(
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        return respond(
            content = "{ invalid json }",
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    // Complex scenarios for integration testing
    suspend fun MockRequestHandleScope.respondWithDeltas(
        requestCount: Int,
        totalDeltas: Int = 3,
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        val hasMore = requestCount < totalDeltas
        val participant = createTestParticipant()
        val metadata = ChatMetadataDto(
            name = "Chat Batch $requestCount",
            participants = listOf(participant),
            pictureUrl = null,
            rules = emptyList(),
            unreadMessagesCount = requestCount,
            lastReadMessageId = null,
            lastActivityAt = TEST_TIMESTAMP.toString(),
        )

        val delta = when (requestCount) {
            1 -> {
                // First request: create the chat
                ChatCreatedDeltaDto(
                    chatId = TEST_CHAT_ID.toString(),
                    chatMetadata = metadata,
                    initialMessages = emptyList(),
                    timestamp = TEST_TIMESTAMP.plusMillis(requestCount * 1000L).toString(),
                )
            }
            totalDeltas -> {
                // Final request: delete the chat
                ChatDeletedDeltaDto(
                    chatId = TEST_CHAT_ID.toString(),
                    timestamp = TEST_TIMESTAMP.plusMillis(requestCount * 1000L).toString(),
                )
            }
            else -> {
                // Middle requests: update the chat
                ChatUpdatedDeltaDto(
                    chatId = TEST_CHAT_ID.toString(),
                    chatMetadata = metadata,
                    messagesToAdd = emptyList(),
                    messagesToDelete = emptyList(),
                    timestamp = TEST_TIMESTAMP.plusMillis(requestCount * 1000L).toString(),
                )
            }
        }

        val deltaList = ChatListDeltaDto(
            changes = listOf(delta),
            fromTimestamp = TEST_TIMESTAMP.plusMillis((requestCount - 1) * 1000L).toString(),
            toTimestamp = TEST_TIMESTAMP.plusMillis(requestCount * 1000L).toString(),
            hasMoreChanges = hasMore,
        )

        val response = ApiResponse(data = deltaList, success = true)
        return respond(
            content = json.encodeToString(response),
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    suspend fun MockRequestHandleScope.respondWithRetryableError(
        attemptNumber: Int,
        successAfterAttempts: Int = 2,
        delayMs: Long = 0,
    ): HttpResponseData {
        if (delayMs > 0) delay(delayMs)

        return if (attemptNumber < successAfterAttempts) {
            respondError(
                status = HttpStatusCode.ServiceUnavailable,
                content = "Service temporarily unavailable",
            )
        } else {
            respondWithSuccessfulChat()
        }
    }

    // Helper functions
    private fun createTestParticipant() = ParticipantDto(
        id = TEST_PARTICIPANT_ID.toString(),
        name = "Test User",
        pictureUrl = null,
        joinedAt = TEST_JOINED_AT.toString(),
        onlineAt = TEST_ONLINE_AT.toString(),
        isAdmin = false,
        isModerator = false,
    )

    private fun createTestMessage(participant: ParticipantDto) = MessageDto(
        id = TEST_MESSAGE_ID.toString(),
        parentId = null,
        sender = participant,
        recipient = TEST_CHAT_ID.toString(),
        createdAt = TEST_TIMESTAMP.toString(),
        sentAt = TEST_TIMESTAMP.toString(),
        deliveredAt = null,
        editedAt = null,
        deliveryStatus = DeliveryStatusDto.Sent,
        content = "Test message content",
    )

    /**
     * Extension function to add Instant.plusMillis for test convenience
     */
    private fun Instant.plusMillis(millis: Long): Instant =
        Instant.fromEpochMilliseconds(toEpochMilliseconds() + millis)
}
