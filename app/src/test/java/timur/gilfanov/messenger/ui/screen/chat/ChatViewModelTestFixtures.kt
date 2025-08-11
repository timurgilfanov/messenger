package timur.gilfanov.messenger.ui.screen.chat

import androidx.paging.PagingData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError

object ChatViewModelTestFixtures {

    fun createTestChat(
        chatId: ChatId,
        currentUserId: ParticipantId,
        otherUserId: ParticipantId,
        messages: List<Message> = emptyList(),
        isOneToOne: Boolean = true,
    ): Chat = DomainTestFixtures.createTestChatWithParticipants(
        chatId = chatId,
        currentUserId = currentUserId,
        otherUserIds = listOf(otherUserId),
        messages = messages,
        isOneToOne = isOneToOne,
    )

    fun createTestMessage(
        senderId: ParticipantId,
        text: String = "Test message",
        deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
        joinedAt: Instant,
        createdAt: Instant,
    ): TextMessage {
        val sender = DomainTestFixtures.createTestParticipant(
            id = senderId,
            name = "Current User",
            joinedAt = joinedAt,
        )

        return DomainTestFixtures.createTestTextMessage(
            sender = sender,
            text = text,
            deliveryStatus = deliveryStatus,
            createdAt = createdAt,
        )
    }

    class ChatRepositoryFake(
        private val chat: Chat? = null,
        private val flowChat: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>? = null,
        private val flowSendMessage: Flow<Message>? = null,
    ) : ChatRepository,
        MessageRepository {

        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, RepositorySendMessageError>> = flowSendMessage?.map {
            ResultWithError.Success<Message, RepositorySendMessageError>(it)
        }
            ?: flowOf(
                ResultWithError.Success<Message, RepositorySendMessageError>(
                    when (message) {
                        is TextMessage -> message.copy(deliveryStatus = Sending(0))
                        else -> message
                    },
                ),
            )

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = flowChat ?: flowOf(
            chat?.let { Success(it) } ?: Failure(ChatNotFound),
        )

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            error("Not implemented")
    }

    class ChatRepositoryFakeWithStatusFlow(chat: Chat, val statuses: List<DeliveryStatus>) :
        ChatRepository,
        MessageRepository {

        private val chatFlow = MutableStateFlow(chat)

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, RepositorySendMessageError>> = flowOf(
            *(
                statuses.map {
                    ResultWithError.Success<Message, RepositorySendMessageError>(
                        (message as TextMessage).copy(deliveryStatus = it),
                    )
                }.toTypedArray()
                ),
        ).onEach { result ->
            val msg = (result as Success).data
            delay(10) // to pass immediate state updates, like text input
            chatFlow.update { currentChat ->
                val messages = currentChat.messages.toMutableList().apply {
                    val indexOfFirst = indexOfFirst { it.id == msg.id }
                    if (indexOfFirst != -1) {
                        this[indexOfFirst] = msg
                    } else {
                        add(msg)
                    }
                }.toImmutableList()
                currentChat.copy(messages = messages)
            }
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatFlow.map { chat ->
            Success(chat)
        }

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            error("Not implemented")
    }
}
