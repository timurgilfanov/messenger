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
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.RepositoryEditMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError

@Category(timur.gilfanov.annotations.Unit::class)
class InMemoryParticipantRepositoryFakeTest {

    @Test
    fun `sendMessage emits message with updated delivery status`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val currentUserId = repository.currentUserId
        val chatIds = listOf(repository.aliceChatId, repository.bobChatId)

        chatIds.forEach { chatId ->
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
                val result0 = awaitItem()
                assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result0)
                val sending0 = result0.data as TextMessage
                assertIs<DeliveryStatus.Sending>(sending0.deliveryStatus)
                assertEquals(0, sending0.deliveryStatus.progress)

                val result50 = awaitItem()
                assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result50)
                val sending50 = result50.data as TextMessage
                assertIs<DeliveryStatus.Sending>(sending50.deliveryStatus)
                assertEquals(50, sending50.deliveryStatus.progress)

                val result100 = awaitItem()
                assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result100)
                val sending100 = result100.data as TextMessage
                assertIs<DeliveryStatus.Sending>(sending100.deliveryStatus)
                assertEquals(100, sending100.deliveryStatus.progress)

                val resultDelivered = awaitItem()
                assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(
                    resultDelivered,
                )
                val delivered = resultDelivered.data as TextMessage
                assertEquals(DeliveryStatus.Delivered, delivered.deliveryStatus)

                val resultRead = awaitItem()
                assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(resultRead)
                val read = resultRead.data as TextMessage
                assertEquals(DeliveryStatus.Read, read.deliveryStatus)

                awaitComplete()
            }
        }
    }

    @Test
    fun `editMessage updates message in chat`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val chatIds = listOf(repository.aliceChatId, repository.bobChatId)

        chatIds.forEach { chatId ->

            repository.receiveChatUpdates(chatId).test {
                val initialChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
                val messageToEdit = initialChat.messages[0]

                val editedMessage = (messageToEdit as TextMessage).copy(
                    text = "Edited message",
                )

                repository.editMessage(editedMessage).test {
                    val result = awaitItem()
                    assertIs<ResultWithError.Success<Message, RepositoryEditMessageError>>(result)
                    assertEquals("Edited message", (result.data as TextMessage).text)
                    awaitComplete()
                }

                val updatedChat = (awaitItem() as Success<Chat, ReceiveChatUpdatesError>).data
                val updatedMessage = updatedChat.messages.find { it.id == messageToEdit.id }
                assertIs<TextMessage>(updatedMessage)
                assertEquals("Edited message", updatedMessage.text)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `flowChatList returns list of chats`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()

        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(2, result.data.size)

            val chat = result.data[0]
            assertEquals(repository.aliceChatId, chat.id)
            assertEquals(2, chat.participants.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteMessage removes message from chat`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val chatIds = listOf(repository.aliceChatId, repository.bobChatId)

        chatIds.forEach { chatId ->

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
    }

    @Test
    fun `receiveChatUpdates emits chat updates`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val chatIds = listOf(repository.aliceChatId, repository.bobChatId)

        chatIds.forEach { chatId ->

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
    }

    @Test
    fun `receiveChatUpdates emits chat updates for bobChatId`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val chatId = repository.bobChatId

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
                text = "New test message for Bob"
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
        val repository = MessengerInMemoryRepositoryFake()
        val chatId = repository.aliceChatId

        val result = repository.joinChat(chatId, null)
        assertIs<Success<Chat, RepositoryJoinChatError>>(result)
        assertEquals(chatId, result.data.id)
    }

    @Test
    fun `leaveChat returns success`() = runTest {
        val repository = MessengerInMemoryRepositoryFake()
        val chatId = repository.aliceChatId

        val result = repository.leaveChat(chatId)
        assertIs<Success<Unit, RepositoryLeaveChatError>>(result)
    }
}
