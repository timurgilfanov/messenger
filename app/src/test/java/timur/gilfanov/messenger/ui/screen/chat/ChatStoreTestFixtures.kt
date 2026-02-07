package timur.gilfanov.messenger.ui.screen.chat

import androidx.paging.PagingData
import kotlin.time.Instant
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError

object ChatStoreTestFixtures {

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

    class MessengerRepositoryFake(
        private val chat: Chat? = null,
        private val flowChat: Flow<
            ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>,
            >? = null,
        private val flowSendMessage: Flow<Message>? = null,
        private val pagedMessages: List<Message>? = null,
    ) : ChatRepository,
        MessageRepository {

        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowSendMessage?.map {
            ResultWithError.Success<Message, SendMessageRepositoryError>(it)
        }
            ?: flowOf(
                ResultWithError.Success<Message, SendMessageRepositoryError>(
                    when (message) {
                        is TextMessage -> message.copy(deliveryStatus = Sending(0))
                        else -> message
                    },
                ),
            )

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> = flowChat ?: flowOf(
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
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = ResultWithError.Success(Unit)

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            pagedMessages?.let { flowOf(PagingData.from(it)) } ?: flowOf(PagingData.empty())
    }

    class MessengerRepositoryFakeWithStatusFlow(chat: Chat, val statuses: List<DeliveryStatus>) :
        ChatRepository,
        MessageRepository {

        private val chatFlow = MutableStateFlow(chat)

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            *(
                statuses.map {
                    Success<Message, SendMessageRepositoryError>(
                        (message as TextMessage).copy(deliveryStatus = it),
                    )
                }.toTypedArray()
                ),
        ).onEach { result ->
            val msg = result.data
            delay(10) // to pass immediate state updates, like text input
            chatFlow.update { currentChat ->
                val messages = currentChat.messages.toMutableList().apply {
                    val indexOfFirst = indexOfFirst { it.id == msg.id }
                    if (indexOfFirst != -1) {
                        this[indexOfFirst] = msg
                    } else {
                        add(msg)
                    }
                }.toPersistentList()
                currentChat.copy(messages = messages)
            }
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> = chatFlow.map { chat ->
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
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = ResultWithError.Success(Unit)

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            flowOf(PagingData.empty())
    }

    /**
     * Test repository that provides synchronized pagination with chat updates.
     *
     * When the chat flow emits a new chat state, the paged messages flow
     * automatically emits the messages from that chat as PagingData.
     * This ensures pagination stays in sync with chat updates during tests.
     */
    class MessengerRepositoryFakeWithPaging(
        private val initialChat: Chat? = null,
        private val chatFlow: Flow<
            ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>,
            >? = null,
    ) : ChatRepository,
        MessageRepository {

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> = chatFlow ?: flowOf(
            initialChat?.let { Success(it) } ?: Failure(ChatNotFound),
        )

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            chatFlow?.flatMapLatest { result ->
                when (result) {
                    is Success -> flowOf(PagingData.from(result.data.messages))
                    is Failure -> flowOf(PagingData.empty())
                }
            } ?: flowOf(
                initialChat?.let { PagingData.from(it.messages) } ?: PagingData.empty(),
            )

        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            Success<Message, SendMessageRepositoryError>(
                when (message) {
                    is TextMessage -> message.copy(deliveryStatus = Sending(0))
                    else -> message
                },
            ),
        )

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")

        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = ResultWithError.Success(Unit)

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")
    }
}
