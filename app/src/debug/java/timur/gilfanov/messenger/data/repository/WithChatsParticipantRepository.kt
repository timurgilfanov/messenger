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

const val USER_ID = "550e8400-e29b-41d4-a716-446655440000"

private const val ALICE_USER_ID = "550e8400-e29b-41d4-a716-446655440001"

private const val BOB_USER_ID = "550e8400-e29b-41d4-a716-446655440005"

const val ALICE_CHAT_ID = "550e8400-e29b-41d4-a716-446655440002"

const val BOB_CHAT_ID = "550e8400-e29b-41d4-a716-446655440006"

const val ALICE_TEXT_1 = "Hello! 👋"

private const val ALICE_TEXT_2 = "How are you doing today?"

const val BOB_TEXT_1 = "Hey there! How's the project going?"

@Singleton
class WithChatsParticipantRepository @Inject constructor() : ParticipantRepository {

    val currentUserId = ParticipantId(UUID.fromString(USER_ID))

    private val currentUser = Participant(
        id = currentUserId,
        name = "You",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val aliceUserId = ParticipantId(UUID.fromString(ALICE_USER_ID))

    private val aliceUser = Participant(
        id = aliceUserId,
        name = "Alice",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val bobUserId = ParticipantId(UUID.fromString(BOB_USER_ID))

    private val bobUser = Participant(
        id = bobUserId,
        name = "Bob",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    val aliceChatId = ChatId(UUID.fromString(ALICE_CHAT_ID))
    private val aliceChat = Chat(
        id = aliceChatId,
        participants = persistentSetOf(currentUser, aliceUser),
        name = "Alice",
        pictureUrl = null,
        rules = persistentSetOf(),
        unreadMessagesCount = 0,
        lastReadMessageId = null,
        messages = persistentListOf(
            TextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003")),
                text = ALICE_TEXT_1,
                parentId = null,
                sender = aliceUser,
                recipient = aliceChatId,
                createdAt = Clock.System.now(),
                deliveryStatus = DeliveryStatus.Read,
            ),
            TextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
                text = ALICE_TEXT_2,
                parentId = null,
                sender = currentUser,
                recipient = aliceChatId,
                createdAt = Clock.System.now(),
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        ),
    )

    val bobChatId = ChatId(UUID.fromString(BOB_CHAT_ID))
    private val bobChat = Chat(
        id = bobChatId,
        participants = persistentSetOf(currentUser, bobUser),
        name = "Bob",
        pictureUrl = null,
        rules = persistentSetOf(),
        unreadMessagesCount = 1,
        lastReadMessageId = null,
        messages = persistentListOf(
            TextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440007")),
                text = BOB_TEXT_1,
                parentId = null,
                sender = bobUser,
                recipient = bobChatId,
                createdAt = Clock.System.now(),
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        ),
    )

    private val chatListFlow = MutableStateFlow(listOf(aliceChat, bobChat))

    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        chatListFlow.map { ResultWithError.Success(it) }

    override fun isChatListUpdating(): Flow<Boolean> = flowOf(false)

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
        println("Receiving updates for chat ID: $chatId")
        val chat = when (chatId) {
            aliceChat.id -> aliceChat
            bobChat.id -> bobChat
            else -> error("Unknown chat ID: $chatId")
        }
        return flowOf(ResultWithError.Success(chat))
    }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> {
        val chat = when (chatId) {
            aliceChat.id -> aliceChat
            bobChat.id -> bobChat
            else -> error("Unknown chat ID: $chatId")
        }
        return ResultWithError.Success(chat)
    }

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
