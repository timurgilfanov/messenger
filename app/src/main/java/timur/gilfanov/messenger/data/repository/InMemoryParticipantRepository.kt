package timur.gilfanov.messenger.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError

@Singleton
class InMemoryParticipantRepository @Inject constructor() : ParticipantRepository {

    private val currentUserId =
        ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    private val otherUserId = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))

    private val currentUser = Participant(
        id = currentUserId,
        name = "You",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val otherUser = Participant(
        id = otherUserId,
        name = "Alice",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val defaultChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
    private val chatFlow = MutableStateFlow(
        Chat(
            id = defaultChatId,
            participants = persistentSetOf(currentUser, otherUser),
            name = "Alice",
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(
                TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003")),
                    text = "Hello! 👋",
                    parentId = null,
                    sender = otherUser,
                    recipient = defaultChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = DeliveryStatus.Read,
                ),
                TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
                    text = "How are you doing today?",
                    parentId = null,
                    sender = currentUser,
                    recipient = defaultChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = DeliveryStatus.Delivered,
                ),
            ),
        ),
    )

    private val chatListFlow = MutableStateFlow(
        listOf(chatFlow.value),
    )

    override suspend fun sendMessage(message: Message): Flow<Message> = flow {
        emit((message as TextMessage).copy(deliveryStatus = DeliveryStatus.Sending(0)))
        delay(500)

        emit(message.copy(deliveryStatus = DeliveryStatus.Sending(50)))
        delay(500)

        emit(message.copy(deliveryStatus = DeliveryStatus.Sending(100)))
        delay(300)

        val deliveredMessage = message.copy(deliveryStatus = DeliveryStatus.Delivered)
        val currentChat = chatFlow.value
        val updatedChat = currentChat.copy(
            messages = currentChat.messages.toMutableList().apply {
                add(deliveredMessage)
            }.toImmutableList(),
        )
        chatFlow.value = updatedChat
        chatListFlow.value = listOf(updatedChat)

        emit(deliveredMessage)

        delay(2000)

        val readMessage = deliveredMessage.copy(deliveryStatus = DeliveryStatus.Read)
        val finalChat = updatedChat.copy(
            messages = updatedChat.messages.toMutableList().apply {
                removeAt(
                    updatedChat.messages.size - 1,
                )
                add(readMessage)
            }.toImmutableList(),
        )
        chatFlow.value = finalChat
        chatListFlow.value = listOf(finalChat)

        emit(readMessage)
    }

    override suspend fun editMessage(message: Message): Flow<Message> = flow {
        delay(500)

        val currentChat = chatFlow.value
        val messageIndex = currentChat.messages.indexOfFirst { it.id == message.id }
        if (messageIndex >= 0) {
            val updatedMessages = currentChat.messages.toMutableList().apply {
                set(messageIndex, message)
            }.toImmutableList()
            val updatedChat = currentChat.copy(messages = updatedMessages)
            chatFlow.value = updatedChat
            chatListFlow.value = listOf(updatedChat)
        }

        emit(message)
    }

    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        chatListFlow.map { ResultWithError.Success(it) }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> {
        val currentChat = chatFlow.value
        val updatedMessages = currentChat.messages.toMutableList().apply {
            removeAll { it.id == messageId }
        }.toImmutableList()
        val updatedChat = currentChat.copy(messages = updatedMessages)
        chatFlow.value = updatedChat
        chatListFlow.value = listOf(updatedChat)

        return ResultWithError.Success(Unit)
    }

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        chatFlow.map { ResultWithError.Success(it) }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> = ResultWithError.Success(chatFlow.value)

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> = ResultWithError.Success(Unit)
}
