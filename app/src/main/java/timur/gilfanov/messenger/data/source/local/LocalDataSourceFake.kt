package timur.gilfanov.messenger.data.source.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDeletedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.ChatUpdatedDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Singleton
@Suppress("TooManyFunctions")
class LocalDataSourceFake @Inject constructor() : LocalDataSource {

    private val chatsFlow = MutableStateFlow<Map<ChatId, Chat>>(emptyMap())
    private val syncTimestamp = MutableStateFlow<Instant?>(null)

    override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        println("LocalDataSourceFake: insertChat called with chat id ${chat.id}")
        chatsFlow.update { currentChats ->
            currentChats + (chat.id to chat)
        }
        return ResultWithError.Success(chat)
    }

    override suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        val currentChats = chatsFlow.value
        if (chat.id !in currentChats) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        chatsFlow.update { currentChats ->
            currentChats + (chat.id to chat)
        }
        return ResultWithError.Success(chat)
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError> {
        val currentChats = chatsFlow.value
        println("LocalDataSourceFake: deleteChat called with chat id $chatId")
        if (chatId !in currentChats) {
            println("LocalDataSourceFake: Chat with id $chatId not found")
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        chatsFlow.update { currentChats ->
            currentChats - chatId
        }
        println("LocalDataSourceFake: Chat with id $chatId deleted successfully")
        return ResultWithError.Success(Unit)
    }

    override suspend fun getChat(chatId: ChatId): ResultWithError<Chat, LocalDataSourceError> {
        val chat = chatsFlow.value[chatId]
        return if (chat != null) {
            ResultWithError.Success(chat)
        } else {
            ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }
    }

    override fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>> =
        chatsFlow.map { chats ->
            println("LocalDataSourceFake: flowChatList called with ${chats.size} chats")
            val chatPreviews = chats.values.map { chat -> ChatPreview.fromChat(chat) }
            ResultWithError.Success(chatPreviews)
        }

    override fun flowChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, LocalDataSourceError>> = chatsFlow.map { chats ->
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
        val currentChats = chatsFlow.value
        val chat = currentChats[chatId]

        if (chat == null) {
            return ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }

        val updatedMessages = chat.messages.toMutableList().apply {
            add(message)
        }.toImmutableList()

        val updatedChat = chat.copy(messages = updatedMessages)
        chatsFlow.update { currentChats ->
            currentChats + (chatId to updatedChat)
        }

        return ResultWithError.Success(message)
    }

    override suspend fun updateMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        val chatId = message.recipient
        val currentChats = chatsFlow.value
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
            }.toImmutableList()

            val updatedChat = chat.copy(messages = updatedMessages)
            chatsFlow.update { currentChats ->
                currentChats + (chatId to updatedChat)
            }

            ResultWithError.Success(message)
        }
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, LocalDataSourceError> {
        val currentChats = chatsFlow.value

        // Find the chat containing the message
        val chatWithMessage = currentChats.values.find { chat ->
            chat.messages.any { it.id == messageId }
        }

        if (chatWithMessage == null) {
            return ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        }

        val updatedMessages = chatWithMessage.messages.toMutableList().apply {
            removeAll { it.id == messageId }
        }.toImmutableList()

        val updatedChat = chatWithMessage.copy(messages = updatedMessages)
        chatsFlow.update { currentChats ->
            currentChats + (chatWithMessage.id to updatedChat)
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun getMessage(
        messageId: MessageId,
    ): ResultWithError<Message, LocalDataSourceError> {
        val currentChats = chatsFlow.value

        // Search through all chats for the message
        for (chat in currentChats.values) {
            val message = chat.messages.find { it.id == messageId }
            if (message != null) {
                return ResultWithError.Success(message)
            }
        }

        return ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
    }

    override suspend fun getMessagesForChat(
        chatId: ChatId,
    ): ResultWithError<List<Message>, LocalDataSourceError> {
        val chat = chatsFlow.value[chatId]
        return if (chat != null) {
            ResultWithError.Success(chat.messages)
        } else {
            ResultWithError.Failure(LocalDataSourceError.ChatNotFound)
        }
    }

    override suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError> {
        val timestamp = syncTimestamp.value
        return ResultWithError.Success(timestamp)
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
                    participants = delta.chatMetadata.participants,
                    name = delta.chatMetadata.name,
                    pictureUrl = delta.chatMetadata.pictureUrl,
                    rules = delta.chatMetadata.rules,
                    unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                    lastReadMessageId = delta.chatMetadata.lastReadMessageId,
                    messages = delta.initialMessages,
                )

                chatsFlow.update { currentChats ->
                    currentChats + (delta.chatId to newChat)
                }
            }
            is ChatUpdatedDelta -> {
                val chat = chatsFlow.value[delta.chatId]!!
                val messages = updateMessages(chat, delta.messagesToAdd, delta.messagesToDelete)

                val updatedChat = chat.copy(
                    participants = delta.chatMetadata.participants,
                    name = delta.chatMetadata.name,
                    pictureUrl = delta.chatMetadata.pictureUrl,
                    rules = delta.chatMetadata.rules,
                    unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                    lastReadMessageId = delta.chatMetadata.lastReadMessageId,
                    messages = messages,
                )

                chatsFlow.update { currentChats ->
                    currentChats + (delta.chatId to updatedChat)
                }
            }
            is ChatDeletedDelta -> {
                chatsFlow.update { currentChats ->
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
        return existingMessages.toImmutableList()
    }

    override suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError> {
        delta.changes.sortedBy { it.timestamp }.forEach { chatDelta ->
            val result = applyChatDelta(chatDelta)
            if (result is ResultWithError.Failure) {
                println(
                    "LocalDataSourceFake: Failed to apply chat delta: $chatDelta, error: ${result.error}",
                )
                return result
            }
            updateLastSyncTimestamp(delta.toTimestamp)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun clearAllData(): ResultWithError<Unit, LocalDataSourceError> {
        chatsFlow.update { emptyMap() }
        syncTimestamp.update { null }
        return ResultWithError.Success(Unit)
    }
}
