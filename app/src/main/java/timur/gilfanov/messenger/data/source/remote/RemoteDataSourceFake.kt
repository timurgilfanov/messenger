package timur.gilfanov.messenger.data.source.remote

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Singleton
@Suppress("TooManyFunctions")
class RemoteDataSourceFake @Inject constructor() : RemoteDataSource {

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
        println("RemoteDataSourceFake: Updated server timestamp to $currentServerTimestamp")
        return currentServerTimestamp
    }

    private fun recordServerOperation(operationKey: String): Instant {
        val timestamp = getNextServerTimestamp()
        println("RemoteDataSourceFake: Recording operation '$operationKey' at $timestamp")
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

    override fun subscribeToChats(): Flow<
        ResultWithError<List<ChatPreview>, RemoteDataSourceError>,
        > =
        serverChatsFlow.map { chats ->
            println("RemoteDataSourceFake: subscribeToChats called with ${chats.size} chats")
            if (connectionStateFlow.value) {
                val chatPreviews = chats.values.map { chat -> ChatPreview.fromChat(chat) }
                println("RemoteDataSourceFake: Returning $chatPreviews chat previews")
                ResultWithError.Success(chatPreviews)
            } else {
                ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
            }
        }

    override fun chatsDeltaUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>> = serverChatsFlow.map { chats ->
        println("RemoteDataSourceFake: chatsDeltaUpdates called with ${chats.size} chats")
        if (!connectionStateFlow.value) {
            ResultWithError.Failure(RemoteDataSourceError.NetworkNotAvailable)
        } else {
            if (since == null) {
                generateFullDelta(chats)
            } else {
                val recentOperations = serverOperationTimestamps.filter { (_, timestamp) ->
                    timestamp > since
                }.toList().sortedBy { it.second }

                println(
                    "RemoteDataSourceFake: Found ${recentOperations.size} recent operations since $since",
                )

                if (recentOperations.isEmpty()) {
                    ResultWithError.Success(
                        ChatListDelta(
                            changes = emptyList<ChatDelta>().toImmutableList(),
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
                            changes = deltas.toImmutableList(),
                            fromTimestamp = since,
                            toTimestamp = latestTimestamp,
                            hasMoreChanges = false,
                        ),
                    )
                }
            }
        }
    }

    @Suppress("LongMethod")
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
                initialMessages = chat.messages.toImmutableList(),
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
            }.toImmutableList()

            ChatUpdatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                messagesToAdd = incrementalMessages,
                messagesToDelete = emptyList<MessageId>().toImmutableList(),
                timestamp = timestamp,
            )
        }

        operationKey.startsWith("delete_message_") && operationKey.contains("_from_") -> {
            val messageIdString = operationKey.substringAfter(
                "delete_message_",
            ).substringBefore("_from_")
            val chatIdString = operationKey.substringAfterLast("_from_")
            val messageId = MessageId(UUID.fromString(messageIdString))
            val chatId = ChatId(UUID.fromString(chatIdString))
            val chat = chats[chatId] ?: return null

            ChatUpdatedDelta(
                chatId = chat.id,
                chatMetadata = ChatMetadata.fromChat(chat),
                messagesToAdd = emptyList<Message>().toImmutableList(),
                messagesToDelete = listOf(messageId).toImmutableList(),
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
                initialMessages = chat.messages.toImmutableList(),
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
            }.toImmutableList()
        } else {
            chat.messages.toMutableList().apply {
                add(message)
            }.toImmutableList()
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
        }.toImmutableList()

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
            }.toImmutableList()

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
                    }.toImmutableList(),
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
                }.toImmutableList()
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
}
