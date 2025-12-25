package timur.gilfanov.messenger.domain.usecase.repository

import androidx.paging.PagingData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.DeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError

class RepositoryFake :
    ChatRepository,
    MessageRepository {
    private val chats = mutableMapOf<ChatId, Chat>()

    private val chatListFlow = MutableSharedFlow<List<Chat>>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val chatUpdates = MutableSharedFlow<Chat>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private suspend fun emitChatList() {
        chatListFlow.emit(chats.values.toList())
    }

    override suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListError>,
        > =
        chatListFlow
            .onStart { emit(chats.values.toList()) }
            .distinctUntilChanged()
            .map { chats -> ResultWithError.Success(chats.map { ChatPreview.fromChat(it) }) }

    override fun isChatListUpdating(): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatError> {
        chats[chat.id] = chat
        chatUpdates.emit(chat)
        emitChatList()
        return ResultWithError.Success(chat)
    }

    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, SendMessageError>> {
        val chatId = message.recipient
        val chat = chats[chatId]!!

        val updatedMessage = when (message) {
            is TextMessage -> message.copy(deliveryStatus = DeliveryStatus.Sent)
            else -> error("Unknown message type")
        }

        val updatedChat = chat.copy(
            messages = chat.messages.add(updatedMessage),
            unreadMessagesCount = chat.unreadMessagesCount + 1,
        )

        chats[chatId] = updatedChat
        chatUpdates.emit(updatedChat)
        emitChatList()

        return flowOf(ResultWithError.Success(updatedMessage))
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, EditMessageError>> {
        val chatId = message.recipient
        val chat = chats[chatId]!!

        val updatedMessage = when (message) {
            is TextMessage -> message.copy(deliveryStatus = DeliveryStatus.Sent)
            else -> error("Unknown message type")
        }

        val updatedMessages = chat.messages.toMutableList().apply {
            val index = indexOfFirst { it.id == message.id }
            if (index != -1) {
                set(index, updatedMessage)
            }
        }

        val updatedChat = chat.copy(
            messages = persistentListOf<Message>().addAll(updatedMessages),
        )

        chats[chatId] = updatedChat
        chatUpdates.emit(updatedChat)
        emitChatList()

        return flowOf(ResultWithError.Success(updatedMessage))
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, DeleteMessageError> {
        error("Not implemented")
    }

    override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> {
        error("Not implemented")
    }

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
        val initialChat = chats[chatId]

        return chatUpdates
            .filter { it.id == chatId }
            .map { ResultWithError.Success<Chat, ReceiveChatUpdatesError>(it) }
            .onStart {
                initialChat?.let { emit(ResultWithError.Success(it)) }
            }
            .distinctUntilChanged()
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, DeleteChatError> {
        if (!chats.containsKey(chatId)) {
            return ResultWithError.Failure(DeleteChatError.ChatNotFound(chatId))
        }

        chats.remove(chatId)
        emitChatList()
        return ResultWithError.Success(Unit)
    }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, JoinChatError> = error("Not yet implemented")

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatError> =
        error("Not yet implemented")

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, MarkMessagesAsReadError> {
        val chat = chats[chatId] ?: return ResultWithError.Success(Unit)
        val upToIndex = chat.messages.indexOfFirst { it.id == upToMessageId }
        val unreadCount = if (upToIndex >= 0) {
            // Messages are stored chronologically (oldest first)
            // When marking "up to" a message, we mark that message and all previous messages as read
            // The unread count should be the number of messages AFTER the upToIndex
            maxOf(0, chat.messages.size - upToIndex - 1)
        } else {
            chat.unreadMessagesCount
        }
        val updatedChat = chat.copy(
            unreadMessagesCount = unreadCount,
            lastReadMessageId = upToMessageId,
        )
        chats[chatId] = updatedChat
        chatUpdates.emit(updatedChat)
        emitChatList()
        return ResultWithError.Success(Unit)
    }
}
