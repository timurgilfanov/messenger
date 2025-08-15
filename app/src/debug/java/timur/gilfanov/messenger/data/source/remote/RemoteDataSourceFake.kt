package timur.gilfanov.messenger.data.source.remote

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
    RemoteSyncDataSource {

    companion object {
        private const val NETWORK_DELAY_MS = 300L
        private const val SENDING_DELAY_MS = 500L
        private const val DELIVERY_DELAY_MS = 300L
        private const val READ_DELAY_MS = 2000L
        private const val SENDING_PROGRESS_START = 0
        private const val SENDING_PROGRESS_MID = 50
        private const val SENDING_PROGRESS_COMPLETE = 100
    }

    private val serverChatsFlow = MutableStateFlow<Map<ChatId, Chat>>(emptyMap())
    private val connectionStateFlow = MutableStateFlow(true)

    // Server-side timestamp tracking
    private var currentServerTimestamp = Instant.fromEpochMilliseconds(0)
    private val serverOperationTimestamps = mutableMapOf<String, Instant>()

    private fun getNextServerTimestamp(): Instant {
        currentServerTimestamp = currentServerTimestamp.plus(1.seconds)
        return currentServerTimestamp
    }

    private fun recordServerOperation(operationKey: String): Instant {
        val timestamp = getNextServerTimestamp()
        serverOperationTimestamps[operationKey] = timestamp
        return timestamp
    }

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        // Record server timestamp for this operation
        recordServerOperation("create_chat_${chat.id.id}")

        serverChatsFlow.update { currentChats ->
            currentChats + (chat.id to chat)
        }

        return ResultWithError.Success(chat)
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentChats = serverChatsFlow.value
        return if (chatId in currentChats) {
            recordServerOperation("delete_chat_${chatId.id}")

            serverChatsFlow.update { currentChats ->
                currentChats - chatId
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

        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val chat = serverChatsFlow.value[chatId]
        return if (chat != null) {
            ResultWithError.Success(chat)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentChats = serverChatsFlow.value
        return if (chatId in currentChats) {
            recordServerOperation("leave_chat_${chatId.id}")

            serverChatsFlow.update { currentChats ->
                currentChats - chatId
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }

    fun subscribeToChats(): Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>> =
        serverChatsFlow.map { chats ->
            if (connectionStateFlow.value) {
                val chatPreviews = chats.values.map { chat -> ChatPreview.fromChat(chat) }
                ResultWithError.Success(chatPreviews)
            } else {
                ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
            }
        }

    override fun chatsDeltaUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>> = serverChatsFlow.map { chats ->
        if (!connectionStateFlow.value) {
            ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        } else {
            if (since == null) {
                generateFullDelta(chats)
            } else {
                val recentOperations = serverOperationTimestamps.filter { (_, timestamp) ->
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
                        generateChatDelta(operationKey, chats, timestamp, since)
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
            val chat = chats[chatId] ?: run {
                return null
            }

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
        val timestamp = getNextServerTimestamp()
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
        if (!connectionStateFlow.value) {
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
            recordServerOperation("update_message_${message.id.id}_in_${message.recipient.id}")
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
        delay(READ_DELAY_MS)

        textMessage.copy(
            deliveryStatus = DeliveryStatus.Read,
        ).let { updatedMessage ->
            recordServerOperation("update_message_${message.id.id}_in_${message.recipient.id}")
            updateMessageInChat(updatedMessage)
            emit(ResultWithError.Success(updatedMessage))
        }
    }

    private fun updateMessageInChat(message: Message) {
        val chatId = message.recipient
        val currentChats = serverChatsFlow.value
        val chat = currentChats[chatId] ?: return

        val messageIndex = chat.messages.indexOfFirst { it.id == message.id }
        val updatedMessages = if (messageIndex >= 0) {
            chat.messages.toMutableList().apply {
                set(messageIndex, message)
            }.toPersistentList()
        } else {
            chat.messages.toMutableList().apply {
                add(message)
            }.toPersistentList()
        }

        val updatedChat = chat.copy(messages = updatedMessages)
        serverChatsFlow.update { currentChats ->
            currentChats + (chatId to updatedChat)
        }
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RemoteDataSourceError>> = flow {
        delay(NETWORK_DELAY_MS)

        if (!connectionStateFlow.value) {
            emit(ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable))
            return@flow
        }

        val chatId = message.recipient
        val currentChats = serverChatsFlow.value
        val chat = currentChats[chatId]

        if (chat == null) {
            emit(ResultWithError.Failure(RemoteDataSourceError.ChatNotFound))
            return@flow
        }

        val messageIndex = chat.messages.indexOfFirst { it.id == message.id }
        if (messageIndex == -1) {
            emit(ResultWithError.Failure(RemoteDataSourceError.MessageNotFound))
            return@flow
        }

        val updatedMessages = chat.messages.toMutableList().apply {
            set(messageIndex, message)
        }.toPersistentList()

        val updatedChat = chat.copy(messages = updatedMessages)
        serverChatsFlow.update { currentChats ->
            currentChats + (chatId to updatedChat)
        }

        emit(ResultWithError.Success(message))
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)

        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentChats = serverChatsFlow.value
        val chatWithMessage = currentChats.values.find { chat ->
            chat.messages.any { it.id == messageId }
        }

        return if (chatWithMessage != null) {
            val updatedMessages = chatWithMessage.messages.toMutableList().apply {
                removeAll { it.id == messageId }
            }.toPersistentList()

            val updatedChat = chatWithMessage.copy(messages = updatedMessages)
            serverChatsFlow.update { currentChats ->
                currentChats + (chatWithMessage.id to updatedChat)
            }

            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.MessageNotFound)
        }
    }

    fun setConnectionState(connected: Boolean) {
        connectionStateFlow.update { connected }
    }

    fun addChatToServer(chat: Chat) {
        recordServerOperation("create_chat_${chat.id.id}")

        serverChatsFlow.update { currentChats ->
            currentChats + (chat.id to chat)
        }
    }

    fun addMessageToServerChat(message: Message) {
        val chatId = message.recipient
        recordServerOperation("add_message_${message.id.id}_in_${chatId.id}")

        serverChatsFlow.update { currentChats ->
            val existingChat = currentChats[chatId]
            if (existingChat != null) {
                val updatedChat = existingChat.copy(
                    messages = existingChat.messages.toMutableList().apply {
                        add(message)
                    }.toPersistentList(),
                    unreadMessagesCount = existingChat.unreadMessagesCount + 1,
                )
                currentChats + (chatId to updatedChat)
            } else {
                error("Trying to add message to non-existing chat: $chatId")
            }
        }
    }

    fun deleteMessageFromServerChat(messageId: MessageId) {
        serverChatsFlow.update { currentChats ->
            val chatWithMessage = currentChats.values.find { chat ->
                chat.messages.any { it.id == messageId }
            }

            if (chatWithMessage != null) {
                recordServerOperation(
                    "delete_message_${messageId.id}_from_${chatWithMessage.id.id}",
                )

                val updatedMessages = chatWithMessage.messages.toMutableList().apply {
                    removeAll { it.id == messageId }
                }.toPersistentList()
                val updatedChat = chatWithMessage.copy(
                    messages = updatedMessages,
                    unreadMessagesCount = maxOf(0, chatWithMessage.unreadMessagesCount - 1),
                )
                currentChats + (chatWithMessage.id to updatedChat)
            } else {
                error("Trying to delete message from non-existing chat: $messageId")
            }
        }
    }

    fun deleteChatFromServer(chatId: ChatId) {
        recordServerOperation("delete_chat_${chatId.id}")

        serverChatsFlow.update { currentChats ->
            if (chatId in currentChats) {
                currentChats - chatId
            } else {
                error("Trying to delete non-existing chat from server: $chatId")
            }
        }
    }

    fun clearServerData() {
        serverChatsFlow.update { emptyMap() }
        serverOperationTimestamps.clear()
        currentServerTimestamp = Instant.fromEpochMilliseconds(0)
    }

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RemoteDataSourceError> {
        delay(NETWORK_DELAY_MS)
        if (!connectionStateFlow.value) {
            return ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        }

        val currentChats = serverChatsFlow.value
        val existingChat = currentChats[chatId]

        return if (existingChat != null) {
            existingChat.unreadMessagesCount
            serverChatsFlow.update { chats ->
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

                chats + (
                    chatId to existingChat.copy(
                        unreadMessagesCount = unreadCount,
                        lastReadMessageId = upToMessageId,
                    )
                    )
            }

            // Record server operation for delta sync AFTER updating the state
            recordServerOperation("mark_as_read_${chatId.id}_up_to_${upToMessageId.id}")
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(RemoteDataSourceError.ChatNotFound)
        }
    }
}
