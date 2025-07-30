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
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Read
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
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
class InMemoryParticipantRepositoryFake @Inject constructor() : ParticipantRepository {

    companion object Companion {
        private const val SENDING_DELAY_MS = 500L
        private const val DELIVERY_DELAY_MS = 300L
        private const val READ_DELAY_MS = 2000L
        private const val EDIT_DELAY_MS = 500L
        private const val SENDING_PROGRESS_START = 0
        private const val SENDING_PROGRESS_MID = 50
        private const val SENDING_PROGRESS_COMPLETE = 100
    }

    val currentUserId =
        ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    private val aliceUserId = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
    private val bobUserId = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440005"))

    private val currentUser = Participant(
        id = currentUserId,
        name = "You",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val aliceUser = Participant(
        id = aliceUserId,
        name = "Alice",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    private val bobUser = Participant(
        id = bobUserId,
        name = "Bob",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
        onlineAt = Clock.System.now(),
    )

    val aliceChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
    private val aliceChatFlow = MutableStateFlow(
        Chat(
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
                    text = "Hello! 👋",
                    parentId = null,
                    sender = aliceUser,
                    recipient = aliceChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = Read,
                ),
                TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
                    text = "How are you doing today?",
                    parentId = null,
                    sender = currentUser,
                    recipient = aliceChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = Delivered,
                ),
            ),
        ),
    )

    val bobChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440006"))

    private val bobChatFlow = MutableStateFlow(
        Chat(
            id = bobChatId,
            participants = persistentSetOf(currentUser, bobUser),
            name = "Bob",
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(
                TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440007")),
                    text = "Hey there! 🌟",
                    parentId = null,
                    sender = bobUser,
                    recipient = bobChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = Read,
                ),
                TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440008")),
                    text = "How's your day going?",
                    parentId = null,
                    sender = currentUser,
                    recipient = bobChatId,
                    createdAt = Clock.System.now(),
                    deliveryStatus = Delivered,
                ),
            ),
        ),
    )

    private val chatListFlow = MutableStateFlow(
        listOf(aliceChatFlow.value, bobChatFlow.value),
    )

    private val isChatListUpdatingFlow = MutableStateFlow(false)

    override suspend fun sendMessage(message: Message): Flow<Message> = flow {
        fun updateChat(message: Message) {
            val targetChatId = message.recipient
            when (targetChatId) {
                aliceChatId -> {
                    val currentChat = aliceChatFlow.value
                    val updatedChat = currentChat.copy(
                        messages = currentChat.messages.toMutableList().apply {
                            if (message.id in this.map { it.id }) {
                                // If message already exists, update it
                                val index = indexOfFirst { it.id == message.id }
                                set(index, message)
                            } else {
                                // Otherwise, add new message
                                add(message)
                            }
                        }.toImmutableList(),
                    )
                    aliceChatFlow.update { updatedChat }
                    chatListFlow.update { listOf(updatedChat, bobChatFlow.value) }
                }
                bobChatId -> {
                    val currentChat = bobChatFlow.value
                    val updatedChat = currentChat.copy(
                        messages = currentChat.messages.toMutableList().apply {
                            if (message.id in this.map { it.id }) {
                                // If message already exists, update it
                                val index = indexOfFirst { it.id == message.id }
                                set(index, message)
                            } else {
                                // Otherwise, add new message
                                add(message)
                            }
                        }.toImmutableList(),
                    )
                    bobChatFlow.update { updatedChat }
                    chatListFlow.update { listOf(aliceChatFlow.value, updatedChat) }
                }
            }
        }

        (message as TextMessage).copy(
            deliveryStatus = Sending(SENDING_PROGRESS_START),
        ).let { message ->
            updateChat(message)
            emit(message)
        }
        delay(SENDING_DELAY_MS)

        message.copy(deliveryStatus = Sending(SENDING_PROGRESS_MID)).let { message ->
            updateChat(message)
            emit(message)
        }
        delay(SENDING_DELAY_MS)

        message.copy(deliveryStatus = Sending(SENDING_PROGRESS_COMPLETE)).let { message ->
            updateChat(message)
            emit(message)
        }
        delay(DELIVERY_DELAY_MS)

        message.copy(deliveryStatus = Delivered).let { message ->
            updateChat(message)
            emit(message)
        }

        delay(READ_DELAY_MS)

        message.copy(deliveryStatus = Read).let { message ->
            updateChat(message)
            emit(message)
        }
    }

    override suspend fun editMessage(message: Message): Flow<Message> = flow {
        delay(EDIT_DELAY_MS)

        val targetChatId = message.recipient
        when (targetChatId) {
            aliceChatId -> {
                val currentChat = aliceChatFlow.value
                val messageIndex = currentChat.messages.indexOfFirst { it.id == message.id }
                if (messageIndex >= 0) {
                    val updatedMessages = currentChat.messages.toMutableList().apply {
                        set(messageIndex, message)
                    }.toImmutableList()
                    val updatedChat = currentChat.copy(messages = updatedMessages)
                    aliceChatFlow.update { updatedChat }
                    chatListFlow.update { listOf(updatedChat, bobChatFlow.value) }
                }
            }
            bobChatId -> {
                val currentChat = bobChatFlow.value
                val messageIndex = currentChat.messages.indexOfFirst { it.id == message.id }
                if (messageIndex >= 0) {
                    val updatedMessages = currentChat.messages.toMutableList().apply {
                        set(messageIndex, message)
                    }.toImmutableList()
                    val updatedChat = currentChat.copy(messages = updatedMessages)
                    bobChatFlow.update { updatedChat }
                    chatListFlow.update { listOf(aliceChatFlow.value, updatedChat) }
                }
            }
        }

        emit(message)
    }

    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        chatListFlow.map { ResultWithError.Success(it) }

    override fun isChatListUpdating(): Flow<Boolean> = isChatListUpdatingFlow

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> {
        // First, find which chat contains the message
        val defaultChat = aliceChatFlow.value
        val secondChat = bobChatFlow.value

        when {
            defaultChat.messages.any { it.id == messageId } -> {
                val updatedMessages = defaultChat.messages.toMutableList().apply {
                    removeAll { it.id == messageId }
                }.toImmutableList()
                val updatedChat = defaultChat.copy(messages = updatedMessages)
                aliceChatFlow.update { updatedChat }
                chatListFlow.update { listOf(updatedChat, bobChatFlow.value) }
            }
            secondChat.messages.any { it.id == messageId } -> {
                val updatedMessages = secondChat.messages.toMutableList().apply {
                    removeAll { it.id == messageId }
                }.toImmutableList()
                val updatedChat = secondChat.copy(messages = updatedMessages)
                bobChatFlow.update { updatedChat }
                chatListFlow.update { listOf(aliceChatFlow.value, updatedChat) }
            }
        }

        return ResultWithError.Success(Unit)
    }

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = when (chatId) {
        aliceChatId -> aliceChatFlow.map { ResultWithError.Success(it) }
        bobChatId -> bobChatFlow.map { ResultWithError.Success(it) }
        else -> throw IllegalArgumentException("Unknown chat ID: $chatId")
    }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> = ResultWithError.Success(aliceChatFlow.value)

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> = ResultWithError.Success(Unit)
}
