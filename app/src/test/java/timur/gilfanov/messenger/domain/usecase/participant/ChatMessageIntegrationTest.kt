package timur.gilfanov.messenger.domain.usecase.participant

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.data.repository.RepositoryFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.CreateMessageError
import timur.gilfanov.messenger.domain.usecase.participant.message.CreateMessageUseCase
import timur.gilfanov.messenger.domain.usecase.privileged.CreateChatError
import timur.gilfanov.messenger.domain.usecase.privileged.CreateChatUseCase

class ChatMessageIntegrationTest {

    private class ChatValidatorFake : ChatValidator {
        override fun validateOnCreation(chat: Chat): ResultWithError<Unit, ChatValidationError> =
            ResultWithError.Success(Unit)
    }

    private class DeliveryStatusValidatorFake : DeliveryStatusValidator {
        override fun validate(
            currentStatus: DeliveryStatus?,
            newStatus: DeliveryStatus?,
        ): ResultWithError<Unit, DeliveryStatusValidationError> = ResultWithError.Success(Unit)
    }

    @Test
    fun `create chat, add message and receive update`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant(customTime)

        val initialChat = buildChat {
            id = chatId
            participants = persistentSetOf(participant)
        }

        val messageId = MessageId(UUID.randomUUID())
        val message = buildTextMessage {
            id = messageId
            text = "Hello, this is a test message"
            sender = participant
            recipient = chatId
            createdAt = customTime
        }

        val updatedMessage = message.copy(deliveryStatus = DeliveryStatus.Sent)
        val updatedChat = initialChat.copy(
            messages = persistentListOf(updatedMessage),
            unreadMessagesCount = 1,
        )

        val repository = RepositoryFake()
        val chatValidator = ChatValidatorFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val createChatUseCase = CreateChatUseCase(initialChat, repository, chatValidator)
        val createMessageUseCase = CreateMessageUseCase(
            chat = initialChat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(chatId, repository)

        val chatResult = createChatUseCase()
        assertIs<ResultWithError.Success<Chat, CreateChatError>>(chatResult)
        assertEquals(initialChat, chatResult.data)

        receiveChatUpdatesUseCase().test {
            val initialUpdate = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(initialUpdate)
            assertEquals(initialChat, initialUpdate.data)
            assertEquals(0, initialUpdate.data.messages.size)

            createMessageUseCase().test {
                val messageResult = awaitItem()
                assertIs<ResultWithError.Success<Message, CreateMessageError>>(messageResult)
                val resultMessage = messageResult.data
                assertIs<TextMessage>(resultMessage)
                assertEquals(message.id, resultMessage.id)
                assertEquals(message.text, resultMessage.text)
                assertEquals(DeliveryStatus.Sent, resultMessage.deliveryStatus)
                awaitComplete()
            }

            val messageUpdate = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(messageUpdate)
            assertEquals(updatedChat, messageUpdate.data)
            assertEquals(1, messageUpdate.data.messages.size)
            assertEquals(updatedMessage, messageUpdate.data.messages[0])
            assertEquals(1, messageUpdate.data.unreadMessagesCount)
        }
    }

    private fun createParticipant(customTime: Instant): Participant = Participant(
        id = ParticipantId(UUID.randomUUID()),
        name = "Test User",
        pictureUrl = null,
        joinedAt = customTime,
    )
}
