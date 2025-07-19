package timur.gilfanov.messenger.ui.screen.chat

import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound

object ChatViewModelTestFixtures {

    fun createTestChat(
        chatId: ChatId = ChatId(UUID.randomUUID()),
        currentUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        otherUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        messages: List<Message> = emptyList(),
        isOneToOne: Boolean = true,
    ): Chat {
        val currentUser = buildParticipant {
            id = currentUserId
            name = "Current User"
            joinedAt = Instant.fromEpochMilliseconds(1000)
        }

        val otherUser = buildParticipant {
            id = otherUserId
            name = "Other User"
            joinedAt = Instant.fromEpochMilliseconds(1000)
        }

        return buildChat {
            id = chatId
            name = if (isOneToOne) "Direct Message" else "Group Chat"
            participants = persistentSetOf(currentUser, otherUser)
            this.messages = persistentListOf<Message>().addAll(messages)
            this.isOneToOne = isOneToOne
        }
    }

    fun createTestMessage(
        senderId: ParticipantId,
        text: String = "Test message",
        deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
        joinedAt: Instant,
        createdAt: Instant,
    ): TextMessage {
        val sender = buildParticipant {
            id = senderId
            name = "Current User"
            this.joinedAt = joinedAt
        }

        return buildTextMessage {
            this.sender = sender
            this.text = text
            this.deliveryStatus = deliveryStatus
            this.createdAt = createdAt
        }
    }

    class RepositoryFake(
        private val chat: Chat? = null,
        private val flowChat: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>? = null,
        private val flowSendMessage: Flow<Message>? = null,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {

        override suspend fun sendMessage(message: Message): Flow<Message> =
            flowSendMessage ?: flowOf(
                when (message) {
                    is TextMessage -> message.copy(deliveryStatus = Sending(0))
                    else -> message
                },
            )

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = flowChat ?: flowOf(
            chat?.let { Success(it) } ?: Failure(ChatNotFound),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun generateParentChain(job: Job): String {
        val parents = mutableListOf<Job>()
        var current: Job? = job
        while (current?.parent != null) {
            parents.add(current.parent!!)
            current = current.parent
        }
        return parents.joinToString(" -> ") { it.toString() }
    }

    class RepositoryFakeWithStatusFlow(chat: Chat, val statuses: List<DeliveryStatus>) :
        ParticipantRepository by ParticipantRepositoryNotImplemented() {

        private val chatFlow = MutableStateFlow(chat)

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun sendMessage(message: Message): Flow<Message> = flowOf(
            *(statuses.map { (message as TextMessage).copy(deliveryStatus = it) }.toTypedArray()),
        ).onEach { msg ->
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
                println(
                    "[${Thread.currentThread().name}, parents ${generateParentChain(
                        coroutineContext[Job]!!,
                    )}] " +
                        "Updated message with status ${messages.first().deliveryStatus}, ",
                )
                currentChat.copy(messages = messages)
            }
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatFlow.map { chat ->
            println(
                "[${Thread.currentThread().name}, parents ${generateParentChain(
                    coroutineContext[Job]!!,
                )}] Updated chat with status ${chat.messages.firstOrNull()?.deliveryStatus}",
            )
            Success(chat)
        }
    }
}
