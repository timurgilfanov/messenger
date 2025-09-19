package timur.gilfanov.messenger.data.source.remote

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

@Singleton
@Suppress("TooManyFunctions")
class RemoteDataSourceFake @Inject constructor() :
    RemoteChatDataSource,
    RemoteMessageDataSource,
    RemoteSyncDataSource,
    RemoteDebugDataSource {

    companion object {
        private const val NETWORK_DELAY_MS = 300L
        private const val SENDING_DELAY_MS = 500L
        private const val DELIVERY_DELAY_MS = 300L
        private const val READ_DELAY_MS = 2000L
        private const val SENDING_PROGRESS_START = 0
        private const val SENDING_PROGRESS_MID = 50
        private const val SENDING_PROGRESS_COMPLETE = 100
    }

    private val serverState = MutableStateFlow(ServerState())
    private val connectionState = MutableStateFlow(true)

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        serverState.update { state ->
            state
                .recordOperation("create_chat_${chat.id.id}")
                .copy(chats = state.chats + (chat.id to chat))
        }

        return ResultWithError.Success(chat)
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentState = serverState.value
        return if (chatId in currentState.chats) {
            serverState.update { state ->
                state
                    .recordOperation("delete_chat_${chatId.id}")
                    .copy(chats = state.chats - chatId)
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val chat = serverState.value.chats[chatId]
        return if (chat != null) {
            ResultWithError.Success(chat)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentState = serverState.value
        return if (chatId in currentState.chats) {
            serverState.update { state ->
                state
                    .recordOperation("leave_chat_${chatId.id}")
                    .copy(chats = state.chats - chatId)
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    override val chatPreviews: Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>> =
        serverState.map { state ->
            if (connectionState.value) {
                val chatPreviews = state.chats.values.map { chat -> ChatPreview.fromChat(chat) }
                ResultWithError.Success(chatPreviews)
            } else {
                ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
            }
        }

    override fun observeChatListUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>> = serverState.map { state ->
        if (!connectionState.value) {
            ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        } else {
            if (since == null) {
                generateFullDelta(state.chats)
            } else {
                val recentOperations = state.operationTimestamps.filter { (_, timestamp) ->
                    timestamp > since
                }.toList().sortedBy { it.second }

                if (recentOperations.isEmpty()) {
                    ResultWithError.Success(
                        ChatListDelta(
                            changes = emptyList<ChatDelta>().toPersistentList(),
                            fromTimestamp = since,
                            toTimestamp = since, // Keep original timestamp when no changes
                            hasMoreChanges = false,
                        ),
                    )
                } else {
                    val deltas = recentOperations.mapNotNull { (operationKey, timestamp) ->
                        generateChatDelta(operationKey, state.chats, timestamp, since)
                    }

                    val latestTimestamp = recentOperations.maxOfOrNull { it.second } ?: since
                    ResultWithError.Success(
                        ChatListDelta(
                            changes = deltas.toPersistentList(),
                            fromTimestamp = since,
                            toTimestamp = latestTimestamp,
                            hasMoreChanges = false,
                        ),
                    )
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun generateChatDelta(
        operationKey: String,
        chats: Map<ChatId, Chat>,
        timestamp: Instant,
        since: Instant?,
    ): ChatDelta? = when {
        operationKey.startsWith("create_chat_") -> {
            val chatId = operationKey.substringAfterLast("_")
            val chat = chats[ChatId(UUID.fromString(chatId))]!!
            ChatCreatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                initialMessages = chat.messages.toPersistentList(),
                timestamp = timestamp,
            )
        }

        operationKey.startsWith("delete_chat_") ||
            operationKey.startsWith("leave_chat_") -> {
            val chatId = operationKey.substringAfterLast("_")
            ChatDeletedDelta(
                chatId = ChatId(UUID.fromString(chatId)),
                timestamp = timestamp,
            )
        }

        operationKey.startsWith("add_message_") ||
            operationKey.startsWith("update_message_") -> {
            val chatId = when {
                operationKey.contains("_to_") -> operationKey.substringAfterLast("_to_")
                operationKey.contains("_in_") -> operationKey.substringAfterLast("_in_")
                else -> error("Unexpected operation key format: $operationKey")
            }
            val chat = chats[ChatId(UUID.fromString(chatId))]!!
            val incrementalMessages = if (since != null) {
                chat.messages.filter { it.createdAt > since }
            } else {
                chat.messages
            }.toPersistentList()

            ChatUpdatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                messagesToAdd = incrementalMessages,
                messagesToDelete = emptyList<MessageId>().toPersistentList(),
                timestamp = timestamp,
            )
        }

        operationKey.startsWith("delete_message_") && operationKey.contains("_from_") -> {
            val messageIdString = operationKey
                .substringAfter("delete_message_")
                .substringBefore("_from_")
            val chatIdString = operationKey.substringAfterLast("_from_")
            val messageId = MessageId(UUID.fromString(messageIdString))
            val chatId = ChatId(UUID.fromString(chatIdString))
            val chat = chats[chatId] ?: return null

            ChatUpdatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                messagesToAdd = emptyList<Message>().toPersistentList(),
                messagesToDelete = listOf(messageId).toPersistentList(),
                timestamp = timestamp,
            )
        }

        operationKey.startsWith("mark_as_read_") && operationKey.contains("_up_to_") -> {
            val chatIdString = operationKey
                .substringAfter("mark_as_read_")
                .substringBefore("_up_to_")
            val chatId = ChatId(UUID.fromString(chatIdString))
            val chat = chats[chatId]!!

            ChatUpdatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                messagesToAdd = emptyList<Message>().toPersistentList(),
                messagesToDelete = emptyList<MessageId>().toPersistentList(),
                timestamp = timestamp,
            )
        }

        else -> null
    }

    private fun generateFullDelta(
        chats: Map<ChatId, Chat>,
    ): ResultWithError.Success<ChatListDelta, RemoteDataSourceError> {
        val timestamp = serverState.value.currentTimestamp
        val createDeltas = chats.values.map { chat ->
            ChatCreatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                initialMessages = chat.messages.toPersistentList(),
                timestamp = timestamp,
            )
        }
        return ResultWithError.Success(
            ChatListDelta.fullSync(createDeltas, timestamp),
        )
    }

    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RemoteDataSourceError>> = flow {
        if (!connectionState.value) {
            emit(
                ResultWithError.Failure<Message, RemoteDataSourceError>(
                    RemoteDataSourceError.NetworkNotAvailable,
                ),
            )
            return@flow
        }

        val textMessage = message as TextMessage

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Sending(SENDING_PROGRESS_START),
        ).let { updatedMessage ->
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
        delay(SENDING_DELAY_MS)

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Sending(SENDING_PROGRESS_MID),
        ).let { updatedMessage ->
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
        delay(SENDING_DELAY_MS)

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Sending(SENDING_PROGRESS_COMPLETE),
        ).let { updatedMessage ->
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
        delay(DELIVERY_DELAY_MS)

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Delivered,
        ).let { updatedMessage ->
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
        delay(READ_DELAY_MS)

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Read,
        ).let { updatedMessage ->
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
    }

    private fun updateMessageInChat(message: Message) {
        val chatId = message.recipient

        serverState.update { state ->
            val chat = state.chats[chatId] ?: return@update state

            val messageIndex = chat.messages.indexOfFirst { it.id == message.id }
            val updatedMessages = if (messageIndex >= 0) {
                chat.messages.set(messageIndex, message)
            } else {
                chat.messages.add(message)
            }

            val updatedChat = chat.copy(messages = updatedMessages)
            state
                .recordOperation("update_message_${message.id.id}_in_${chatId.id}")
                .copy(chats = state.chats + (chatId to updatedChat))
        }
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RemoteDataSourceError>> = flow {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            emit(ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable))
            return@flow
        }

        val chatId = message.recipient
        val currentState = serverState.value
        val chat = currentState.chats[chatId]

        if (chat == null) {
            emit(ResultWithError.Failure(RemoteDataSourceError.ChatNotFound))
            return@flow
        }

        val messageIndex = chat.messages.indexOfFirst { it.id == message.id }
        if (messageIndex == -1) {
            emit(ResultWithError.Failure(RemoteDataSourceError.MessageNotFound))
            return@flow
        }

        val updatedMessages = chat.messages.set(messageIndex, message)

        val updatedChat = chat.copy(messages = updatedMessages)
        serverState.update { state ->
            state
                .recordOperation("update_message_${message.id.id}_in_${chatId.id}")
                .copy(chats = state.chats + (chatId to updatedChat))
        }

        emit(ResultWithError.Success(message))
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentState = serverState.value
        val chatWithMessage = currentState.chats.values.find { chat ->
            chat.messages.any { it.id == messageId }
        }

        return if (chatWithMessage != null) {
            val updatedMessages = chatWithMessage.messages.toMutableList().apply {
                removeAll { it.id == messageId }
            }.toPersistentList()

            val updatedChat = chatWithMessage.copy(messages = updatedMessages)
            serverState.update { state ->
                state
                    .recordOperation("delete_message_${messageId.id}_from_${chatWithMessage.id.id}")
                    .copy(chats = state.chats + (chatWithMessage.id to updatedChat))
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.MessageNotFound)
        }
    }

    fun setConnectionState(connected: Boolean) {
        connectionState.update { connected }
    }

    override fun addChat(chat: Chat) {
        serverState.update { state ->
            state
                .recordOperation("create_chat_${chat.id.id}")
                .copy(chats = state.chats + (chat.id to chat))
        }
    }

    override fun addMessage(message: Message) {
        val chatId = message.recipient

        serverState.update { state ->
            val existingChat = state.chats[chatId]
            if (existingChat != null) {
                val updatedChat = existingChat.copy(
                    messages = existingChat.messages.add(message),
                    unreadMessagesCount = existingChat.unreadMessagesCount + 1,
                )
                state
                    .recordOperation("add_message_${message.id.id}_in_${chatId.id}")
                    .copy(chats = state.chats + (chatId to updatedChat))
            } else {
                error("Trying to add message to non-existing chat: $chatId")
            }
        }
    }

    fun deleteMessageFromServerChat(messageId: MessageId) {
        serverState.update { state ->
            val chatWithMessage = state.chats.values.find { chat ->
                chat.messages.any { it.id == messageId }
            }

            if (chatWithMessage != null) {
                val updatedMessages = chatWithMessage.messages.toMutableList().apply {
                    removeAll { it.id == messageId }
                }.toPersistentList()
                val updatedChat = chatWithMessage.copy(
                    messages = updatedMessages,
                    unreadMessagesCount = maxOf(0, chatWithMessage.unreadMessagesCount - 1),
                )

                state
                    .recordOperation("delete_message_${messageId.id}_from_${chatWithMessage.id.id}")
                    .copy(chats = state.chats + (chatWithMessage.id to updatedChat))
            } else {
                error("Trying to delete message from non-existing chat: $messageId")
            }
        }
    }

    fun deleteChatFromServer(chatId: ChatId) {
        serverState.update { state ->
            if (chatId in state.chats) {
                state
                    .recordOperation("delete_chat_${chatId.id}")
                    .copy(chats = state.chats - chatId)
            } else {
                error("Trying to delete non-existing chat from server: $chatId")
            }
        }
    }

    override fun clearData() {
        serverState.update { state ->
            // Start from just after the last sync point to ensure new operations are newer than any sync
            // This provides deterministic behavior while preventing sync race conditions
            val resetTimestamp = state.lastSyncTimestamp.plus(1.seconds)
            ServerState(
                chats = emptyMap(),
                operationTimestamps = emptyMap(),
                currentTimestamp = resetTimestamp,
                // Preserve sync history to maintain continuity
                lastSyncTimestamp = state.lastSyncTimestamp,
            )
        }
    }

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)
        if (!connectionState.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentState = serverState.value
        val existingChat = currentState.chats[chatId]

        return if (existingChat != null) {
            serverState.update { state ->
                // Find the message index to mark as read up to
                val upToIndex = existingChat.messages.indexOfFirst { it.id == upToMessageId }
                val unreadCount = if (upToIndex >= 0) {
                    // Messages are stored chronologically (oldest first)
                    // When marking "up to" a message, we mark that message and all previous messages as read
                    // The unread count should be the number of messages AFTER the upToIndex
                    val remainingUnread = maxOf(0, existingChat.messages.size - upToIndex - 1)
                    remainingUnread
                } else {
                    // Message not found, keep existing unread count
                    existingChat.unreadMessagesCount
                }

                val updatedChat = existingChat.copy(
                    unreadMessagesCount = unreadCount,
                    lastReadMessageId = upToMessageId,
                )

                state
                    .recordOperation("mark_as_read_${chatId.id}_up_to_${upToMessageId.id}")
                    .copy(chats = state.chats + (chatId to updatedChat))
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    override fun getChats(): ImmutableList<Chat> = serverState.value.chats.values.toImmutableList()

    override fun getMessagesSize(): Int = serverState.value.chats.values.sumOf { it.messages.size }
}

/**
 * Unified server state to prevent race conditions between chats and operations.
 * Tracks lastSyncTimestamp to ensure deterministic behavior while preventing sync race conditions.
 */
private data class ServerState(
    val chats: Map<ChatId, Chat> = emptyMap(),
    val operationTimestamps: Map<String, Instant> = emptyMap(),
    val currentTimestamp: Instant = Instant.fromEpochMilliseconds(0),
    val lastSyncTimestamp: Instant = Instant.fromEpochMilliseconds(0),
) {

    fun recordOperation(operationKey: String): ServerState {
        val newTimestamp = currentTimestamp.plus(1.seconds)
        return copy(
            operationTimestamps = operationTimestamps + (operationKey to newTimestamp),
            currentTimestamp = newTimestamp,
            // Track the highest timestamp for sync continuity
            lastSyncTimestamp = maxOf(lastSyncTimestamp, newTimestamp),
        )
    }
}
