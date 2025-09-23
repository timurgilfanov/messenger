package timur.gilfanov.messenger.data.source.local

import androidx.paging.PagingSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDeletedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.ChatUpdatedDelta
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.util.Logger

@Singleton
@Suppress("TooManyFunctions")
class LocalDataSourceFake @Inject constructor(private val logger: Logger) :
    LocalChatDataSource,
    LocalMessageDataSource,
    LocalSyncDataSource,
    LocalDebugDataSource {

    companion object {
        private const val TAG = "LocalDataSourceFake"
    }

    private val chats = MutableStateFlow<Map<ChatId, Chat>>(emptyMap())
    private val syncTimestamp = MutableStateFlow<Instant?>(null)

    // Test control flags for simulating failures
    private var shouldFailGetLastSyncTimestamp = false
    private var shouldFailFlowChatList = false

    override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        logger.d(TAG, "Inserting chat: ${chat.id}")
        chats.update { currentChats ->
            currentChats + (chat.id to chat)
        }
        return ResultWithError.Success(chat)
    }

    override suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        logger.d(TAG, "Updating chat: ${chat.id}")
        val currentChats = chats.value
        if (chat.id !in currentChats) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        chats.update { currentChats ->
            currentChats + (chat.id to chat)
        }
        return ResultWithError.Success(chat)
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError> {
        logger.d(TAG, "Deleting chat: $chatId")
        val currentChats = chats.value
        if (chatId !in currentChats) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        chats.update { currentChats ->
            currentChats - chatId
        }
        return ResultWithError.Success(Unit)
    }

    fun getChat(chatId: ChatId): ResultWithError<Chat, LocalDataSourceError> {
        val chat = chats.value[chatId]
        return if (chat != null) {
            ResultWithError.Success(chat)
        } else {
            ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }
    }

    override fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>> =
        chats.map { chats ->
            if (shouldFailFlowChatList) {
                ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
            } else {
                val chatPreviews = chats.values.map { chat -> ChatPreview.fromChat(chat) }
                ResultWithError.Success(chatPreviews)
            }
        }

    override fun flowChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, LocalDataSourceError>> = chats.map { chats ->
        val chat = chats[chatId]
        if (chat != null) {
            ResultWithError.Success(chat)
        } else {
            ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }
    }

    override suspend fun insertMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        val chatId = message.recipient
        val currentChats = chats.value
        val chat = currentChats[chatId]

        if (chat == null) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        val updatedMessages = chat.messages.toMutableList().apply {
            add(message)
        }.toPersistentList()

        val updatedChat = chat.copy(messages = updatedMessages)
        chats.update { currentChats ->
            currentChats + (chatId to updatedChat)
        }

        return ResultWithError.Success(message)
    }

    override suspend fun updateMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        val chatId = message.recipient
        val currentChats = chats.value
        val chat = currentChats[chatId]

        if (chat == null) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        val messageIndex = chat.messages.indexOfFirst { it.id == message.id }
        return if (messageIndex == -1) {
            ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        } else {
            val updatedMessages = chat.messages.toMutableList().apply {
                set(messageIndex, message)
            }.toPersistentList()

            val updatedChat = chat.copy(messages = updatedMessages)
            chats.update { currentChats ->
                currentChats + (chatId to updatedChat)
            }

            ResultWithError.Success(message)
        }
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
    ): ResultWithError<Unit, LocalDataSourceError> {
        val currentChats = chats.value

        // Find the chat containing the message
        val chatWithMessage = currentChats.values.find { chat ->
            chat.messages.any { it.id == messageId }
        }

        if (chatWithMessage == null) {
            return ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        }

        val updatedMessages = chatWithMessage.messages.toMutableList().apply {
            removeAll { it.id == messageId }
        }.toPersistentList()

        val updatedChat = chatWithMessage.copy(messages = updatedMessages)
        chats.update { currentChats ->
            currentChats + (chatWithMessage.id to updatedChat)
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun getMessage(
        messageId: MessageId,
    ): ResultWithError<Message, LocalDataSourceError> {
        val currentChats = chats.value

        // Search through all chats for the message
        for (chat in currentChats.values) {
            val message = chat.messages.find { it.id == messageId }
            if (message != null) {
                return ResultWithError.Success(message)
            }
        }

        return ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
    }

    override fun getMessagePagingSource(chatId: ChatId): PagingSource<Long, Message> {
        TODO("Not yet implemented")
    }

    override suspend fun getLastSyncTimestamp(): ResultWithError<
        Instant?,
        LocalDataSourceError,
        > =
        if (shouldFailGetLastSyncTimestamp) {
            ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
        } else {
            val timestamp = syncTimestamp.value
            ResultWithError.Success(timestamp)
        }

    override suspend fun updateLastSyncTimestamp(
        timestamp: Instant,
    ): ResultWithError<Unit, LocalDataSourceError> {
        syncTimestamp.update { timestamp }
        return ResultWithError.Success(Unit)
    }

    override suspend fun applyChatDelta(
        delta: ChatDelta,
    ): ResultWithError<Unit, LocalDataSourceError> {
        when (delta) {
            is ChatCreatedDelta -> {
                val newChat = Chat(
                    id = delta.chatId,
                    participants = delta.chatMetadata.participants.toPersistentSet(),
                    name = delta.chatMetadata.name,
                    pictureUrl = delta.chatMetadata.pictureUrl,
                    rules = delta.chatMetadata.rules,
                    unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                    lastReadMessageId = delta.chatMetadata.lastReadMessageId,
                    messages = delta.initialMessages.toPersistentList(),
                )

                chats.update { currentChats ->
                    currentChats + (delta.chatId to newChat)
                }
            }
            is ChatUpdatedDelta -> {
                val chat = chats.value[delta.chatId]!!
                val messages = updateMessages(chat, delta.messagesToAdd, delta.messagesToDelete)

                val updatedChat = chat.copy(
                    participants = delta.chatMetadata.participants.toPersistentSet(),
                    name = delta.chatMetadata.name,
                    pictureUrl = delta.chatMetadata.pictureUrl,
                    rules = delta.chatMetadata.rules,
                    unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                    lastReadMessageId = delta.chatMetadata.lastReadMessageId,
                    messages = messages.toPersistentList(),
                )

                chats.update { currentChats ->
                    currentChats + (delta.chatId to updatedChat)
                }
            }
            is ChatDeletedDelta -> {
                chats.update { currentChats ->
                    currentChats - delta.chatId
                }
            }
        }
        return ResultWithError.Success(Unit)
    }

    private fun updateMessages(
        chat: Chat,
        messagesToAdd: ImmutableList<Message>,
        messagesToDelete: ImmutableList<MessageId>,
    ): ImmutableList<Message> {
        val existingMessages = chat.messages.toMutableList()

        messagesToDelete.forEach { messageId ->
            existingMessages.removeAll { it.id == messageId }
        }

        messagesToAdd.forEach { newMessage ->
            if (existingMessages.none { it.id == newMessage.id }) {
                existingMessages.add(newMessage)
            } else {
                val index = existingMessages.indexOfFirst { it.id == newMessage.id }
                if (index >= 0) {
                    existingMessages[index] = newMessage
                }
            }
        }
        return existingMessages.toPersistentList()
    }

    override suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError> {
        logger.d(TAG, "Applying chat list delta: $delta")
        delta.changes.sortedBy { it.timestamp }.forEach { chatDelta ->
            val result = applyChatDelta(chatDelta)
            if (result is ResultWithError.Failure) {
                return result
            }
            updateLastSyncTimestamp(delta.toTimestamp)
        }
        return ResultWithError.Success(Unit)
    }

    // Test control methods for simulating failures
    fun simulateGetLastSyncTimestampFailure(shouldFail: Boolean) {
        shouldFailGetLastSyncTimestamp = shouldFail
    }

    fun simulateFlowChatListFailure(shouldFail: Boolean) {
        shouldFailFlowChatList = shouldFail
    }

    override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> {
        logger.d(TAG, "Deleting all chats")
        chats.update { emptyMap() }
        return ResultWithError.Success(Unit)
    }

    override suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError> {
        logger.d(TAG, "Deleting all messages")

        // Clear all messages from all chats while keeping the chats themselves
        chats.update { currentChats ->
            currentChats.mapValues { (_, chat) ->
                chat.copy(messages = emptyList<Message>().toPersistentList())
            }
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalDataSourceError> {
        logger.d(TAG, "Clearing sync timestamp")
        syncTimestamp.update { null }
        return ResultWithError.Success(Unit)
    }

    private val _settings = MutableStateFlow(DebugSettings())

    override val settings: Flow<DebugSettings>
        get() = _settings.asStateFlow()

    @Suppress("TooGenericExceptionCaught") // It's a test fake
    override suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, LocalUpdateSettingsError> = try {
        _settings.update { currentSettings ->
            transform(currentSettings)
        }
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        ResultWithError.Failure(LocalUpdateSettingsError.TransformError(e))
    }
}
