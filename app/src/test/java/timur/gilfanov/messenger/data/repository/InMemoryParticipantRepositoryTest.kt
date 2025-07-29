package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Category(timur.gilfanov.annotations.Unit::class)
class InMemoryParticipantRepositoryTest {

    @Test
    fun `sendMessage emits message with updated delivery status`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val currentUserId = repository.currentUserId
        val chatId = repository.aliceChatId

        val sender = buildParticipant {
            id = currentUserId
            name = "You"
            joinedAt = Clock.System.now()
        }

        val message = buildTextMessage {
            id = MessageId(UUID.randomUUID())
            text = "Test message"
            this.sender = sender
            recipient = chatId
            createdAt = Clock.System.now()
        }

        repository.sendMessage(message).test {
            val sending0 = awaitItem()
            assertIs<TextMessage>(sending0)
            assertIs<DeliveryStatus.Sending>(sending0.deliveryStatus)
            assertEquals(0, sending0.deliveryStatus.progress)

            val sending50 = awaitItem()
            assertIs<DeliveryStatus.Sending>(sending50.deliveryStatus)
            assertEquals(50, (sending50.deliveryStatus as DeliveryStatus.Sending).progress)

            val sending100 = awaitItem()
            assertIs<DeliveryStatus.Sending>(sending100.deliveryStatus)
            assertEquals(100, (sending100.deliveryStatus as DeliveryStatus.Sending).progress)

            val delivered = awaitItem()
            assertEquals(DeliveryStatus.Delivered, delivered.deliveryStatus)

            val read = awaitItem()
            assertEquals(DeliveryStatus.Read, read.deliveryStatus)

            awaitComplete()
        }
    }

    @Test
    fun `editMessage updates message in chat`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val chatId = repository.aliceChatId

        repository.receiveChatUpdates(chatId).test {
            val initialChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
            val messageToEdit = initialChat.messages[0]

            val editedMessage = (messageToEdit as TextMessage).copy(
                text = "Edited message",
            )

            repository.editMessage(editedMessage).test {
                val result = awaitItem()
                assertEquals("Edited message", (result as TextMessage).text)
                awaitComplete()
            }

            val updatedChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
            val updatedMessage = updatedChat.messages.find { it.id == messageToEdit.id }
            assertIs<TextMessage>(updatedMessage)
            assertEquals("Edited message", updatedMessage.text)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flowChatList returns list of chats`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()

        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<Success<List<Chat>, FlowChatListError>>(result)
            assertEquals(2, result.data.size)

            val chat = result.data[0]
            assertEquals(repository.aliceChatId, chat.id)
            assertEquals(2, chat.messages.size)
            assertEquals(2, chat.participants.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteMessage removes message from chat`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val chatId = repository.aliceChatId

        repository.receiveChatUpdates(chatId).test {
            val initialChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
            val messageToDelete = initialChat.messages[0]

            val result = repository.deleteMessage(
                messageToDelete.id,
                DeleteMessageMode.FOR_EVERYONE,
            )
            assertIs<Success<Unit, *>>(result)

            val updatedChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
            assertTrue(updatedChat.messages.none { it.id == messageToDelete.id })
            assertEquals(initialChat.messages.size - 1, updatedChat.messages.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `receiveChatUpdates emits chat updates`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val chatId = repository.aliceChatId

        repository.receiveChatUpdates(chatId).test {
            val initialResult = awaitItem()
            assertIs<Success<Chat, ReceiveChatUpdatesError>>(initialResult)
            assertEquals(chatId, initialResult.data.id)

            val currentUserId = repository.currentUserId
            val sender = buildParticipant {
                id = currentUserId
                name = "You"
                joinedAt = Clock.System.now()
            }

            val newMessage = buildTextMessage {
                id = MessageId(UUID.randomUUID())
                text = "New test message"
                this.sender = sender
                recipient = chatId
                createdAt = Clock.System.now()
            }

            repository.sendMessage(newMessage).test {
                skipItems(5)
                awaitComplete()
            }

            val updatedChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
            assertEquals(3, updatedChat.messages.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `joinChat returns chat`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val chatId = repository.aliceChatId

        val result = repository.joinChat(chatId, null)
        assertIs<Success<Chat, RepositoryJoinChatError>>(result)
        assertEquals(chatId, result.data.id)
    }

    @Test
    fun `leaveChat returns success`() = runTest {
        val repository = InMemoryParticipantRepositoryFake()
        val chatId = repository.aliceChatId

        val result = repository.leaveChat(chatId)
        assertIs<Success<Unit, RepositoryLeaveChatError>>(result)
    }
}
