package timur.gilfanov.messenger.domain.usecase

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.usecase.chat.ChatIsNotValid
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError.DuplicateChatId

class CreateChatUseCaseTest {

    private class RepositoryFake(val error: RepositoryCreateChatError? = null) : Repository {
        val chats = mutableSetOf<Chat>()
        override suspend fun sendMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }
        override suspend fun editMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }
        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, RepositoryCreateChatError> {
            error?.let {
                return Failure(it)
            }
            return if (chats.any { it.id == chat.id }) {
                Failure(DuplicateChatId)
            } else {
                chats.add(chat)
                Success(chat)
            }
        }
        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
            error("Not yet implemented")
        }
    }

    private class ChatValidatorFake(val error: ChatValidationError? = null) : ChatValidator {
        override fun validateOnCreation(chat: Chat): ResultWithError<Unit, ChatValidationError> {
            if (error != null) return Failure(error)
            return Success(Unit)
        }
    }

    @Test
    fun `valid chat creation succeeds`() = runTest {
        val chat = buildChat {}
        val validator = ChatValidatorFake()
        val repository = RepositoryFake()
        val useCase = CreateChatUseCase(chat, repository, validator)
        val result = useCase()
        assertIs<Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)
    }

    @Test
    fun `invalid chat validation fails`() = runTest {
        val chat = buildChat {}
        val validationError = ChatValidationError.EmptyName
        val validator = ChatValidatorFake(validationError)
        val repository = RepositoryFake()
        val useCase = CreateChatUseCase(chat, repository, validator)
        val result = useCase()
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<ChatIsNotValid>(result.error)
        assertEquals(validationError, result.error.error)
    }

    @Test
    fun `repository error handling`() = runTest {
        val validator = ChatValidatorFake()
        val chat = buildChat {}
        val repositoryError = RepositoryCreateChatError.UnknownError
        val repository = RepositoryFake(repositoryError)
        val useCase = CreateChatUseCase(chat, repository, validator)
        val result = useCase()
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertEquals(repositoryError, result.error)
    }

    @Test
    fun `create second chat with same id fails`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val chat = buildChat { id = chatId }
        val validator = ChatValidatorFake()
        val repository = RepositoryFake()
        val result = CreateChatUseCase(chat, repository, validator)()
        assertIs<Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)

        val newParticipant = createParticipant()
        val newChat = buildChat {
            id = chatId
            name = "Second Chat"
            participants = persistentSetOf(newParticipant)
        }
        val newResult = CreateChatUseCase(newChat, repository, validator)()
        assertIs<Failure<Chat, CreateChatError>>(newResult)
        assertEquals(DuplicateChatId, newResult.error)
    }

    // removed createChat, use buildChat pattern
}

private fun createParticipant(): Participant = Participant(
    id = ParticipantId(UUID.randomUUID()),
    name = "Test User",
    pictureUrl = null,
    joinedAt = Clock.System.now(),
)
