package timur.gilfanov.messenger.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class WithChatsParticipantRepository @Inject constructor() : ParticipantRepository {

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

    private val sampleChat = Chat(
        id = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
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
                recipient = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
                createdAt = Clock.System.now(),
                deliveryStatus = DeliveryStatus.Read,
            ),
            TextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
                text = "How are you doing today?",
                parentId = null,
                sender = currentUser,
                recipient = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
                createdAt = Clock.System.now(),
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        ),
    )

    private val chatListFlow = MutableStateFlow(listOf(sampleChat))

    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        chatListFlow.map { ResultWithError.Success(it) }

    override fun isChatListUpdating(): Flow<Boolean> = flowOf(false)

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        flowOf(ResultWithError.Success(sampleChat))

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> = ResultWithError.Success(sampleChat)

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> = ResultWithError.Success(Unit)

    override suspend fun sendMessage(message: Message): Flow<Message> = flowOf()

    override suspend fun editMessage(message: Message): Flow<Message> = flowOf()

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> = ResultWithError.Success(Unit)
}
