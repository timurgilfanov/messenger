package timur.gilfanov.messenger.data.repository

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
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.RepositoryCreateChatError

class RepositoryFake : Repository {
    private val chats = mutableMapOf<ChatId, Chat>()

    private val chatUpdates = MutableSharedFlow<Chat>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError> {
        chats[chat.id] = chat
        chatUpdates.emit(chat)
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

        return flowOf(updatedMessage)
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

    private fun <T> ImmutableList<T>.add(item: T): ImmutableList<T> {
        val mutableList = this.toMutableList()
        mutableList.add(item)
        return persistentListOf<T>().addAll(mutableList)
    }
}
