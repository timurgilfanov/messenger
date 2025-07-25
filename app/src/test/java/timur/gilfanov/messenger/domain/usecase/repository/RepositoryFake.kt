package timur.gilfanov.messenger.domain.usecase.repository

import kotlinx.collections.immutable.ImmutableList
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
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepository
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryDeleteChatError

class RepositoryFake :
    ParticipantRepository by ParticipantRepositoryNotImplemented(),
    PrivilegedRepository by PrivilegedRepositoryNotImplemented() {
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

    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        chatListFlow
            .onStart { emit(chats.values.toList()) }
            .distinctUntilChanged()
            .map { ResultWithError.Success(it) }

    override fun isChatListUpdating(): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError> {
        chats[chat.id] = chat
        chatUpdates.emit(chat)
        emitChatList()
        return ResultWithError.Success(chat)
    }

    override suspend fun sendMessage(message: Message): Flow<Message> {
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

        return flowOf(updatedMessage)
    }

    override suspend fun editMessage(message: Message): Flow<Message> {
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

        return flowOf(updatedMessage)
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> {
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

    override suspend fun deleteChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryDeleteChatError> {
        if (!chats.containsKey(chatId)) {
            return ResultWithError.Failure(RepositoryDeleteChatError.ChatNotFound(chatId))
        }

        chats.remove(chatId)
        emitChatList()
        return ResultWithError.Success(Unit)
    }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> = error("Not yet implemented")

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> = error("Not yet implemented")

    private fun <T> ImmutableList<T>.add(item: T): ImmutableList<T> {
        val mutableList = this.toMutableList()
        mutableList.add(item)
        return persistentListOf<T>().addAll(mutableList)
    }
}
