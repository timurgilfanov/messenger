package timur.gilfanov.messenger.domain.usecase.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError

@Category(Unit::class)
class RepositoryFakeTest {

    @Test
    fun `flowChatList emits initial list and updates`() = runTest {
        val repository = RepositoryFake()
        val customTime = Instant.fromEpochMilliseconds(1000)
        val participant = createParticipant(customTime)

        val chat1 = Chat(
            id = ChatId(UUID.randomUUID()),
            name = "Chat 1",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        repository.createChat(chat1)

        repository.flowChatList().test {
            val initial = awaitItem()
            assertIs<Success<List<Chat>, FlowChatListError>>(initial)
            assertEquals(listOf(chat1), initial.data)

            val chat2 = chat1.copy(id = ChatId(UUID.randomUUID()), name = "Chat 2")
            repository.createChat(chat2)

            val second = awaitItem()
            assertIs<Success<List<Chat>, FlowChatListError>>(second)
            assertEquals(listOf(chat1, chat2), second.data)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `receiveChatUpdates emits initial chat and updates after message creation`() = runTest {
        val repository = RepositoryFake()
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant(customTime)

        val initialChat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val createResult = repository.createChat(initialChat)
        assertIs<Success<Chat, ReceiveChatUpdatesError>>(createResult)

        repository.receiveChatUpdates(chatId).test {
            val initialUpdate = awaitItem()
            assertIs<Success<Chat, ReceiveChatUpdatesError>>(initialUpdate)
            assertEquals(initialChat, initialUpdate.data)
            assertEquals(0, initialUpdate.data.messages.size)

            val messageId = MessageId(UUID.randomUUID())
            val message = TextMessage(
                id = messageId,
                parentId = null,
                text = "Hello, this is a test message",
                sender = participant,
                recipient = chatId,
                createdAt = customTime,
                deliveryStatus = null,
            )

            var updatedMessage: Message? = null
            repository.sendMessage(message).test {
                updatedMessage = awaitItem()
                assertIs<TextMessage>(updatedMessage)
                val textMessage = updatedMessage as TextMessage
                assertEquals(message.id, textMessage.id)
                assertEquals(message.text, textMessage.text)
                assertEquals(DeliveryStatus.Sent, textMessage.deliveryStatus)
                awaitComplete()
            }
            val updatedChatResult = awaitItem()
            assertIs<Success<Chat, ReceiveChatUpdatesError>>(updatedChatResult)
            assertEquals(1, updatedChatResult.data.messages.size)
            assertEquals(updatedMessage, updatedChatResult.data.messages[0])
            assertEquals(1, updatedChatResult.data.unreadMessagesCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createParticipant(customTime: Instant): Participant = Participant(
        id = ParticipantId(UUID.randomUUID()),
        name = "Test User",
        pictureUrl = null,
        joinedAt = customTime,
        onlineAt = null,
    )
}
