package timur.gilfanov.messenger.domain.usecase.privileged

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.data.repository.PrivilegedNotImplemented
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator

class CreateChatUseCaseTest {

    private class RepositoryFake(val error: RepositoryCreateChatError? = null) :
        PrivilegedRepository by PrivilegedNotImplemented() {
        val chats = mutableSetOf<Chat>()

        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, RepositoryCreateChatError> {
            error?.let {
                return ResultWithError.Failure(it)
            }
            return if (chats.any { it.id == chat.id }) {
                ResultWithError.Failure(RepositoryCreateChatError.DuplicateChatId)
            } else {
                chats.add(chat)
                ResultWithError.Success(chat)
            }
        }
    }

    private class ChatValidatorFake(val error: ChatValidationError? = null) : ChatValidator {
        override fun validateOnCreation(chat: Chat): ResultWithError<Unit, ChatValidationError> {
            if (error != null) return ResultWithError.Failure(error)
            return ResultWithError.Success(Unit)
        }
    }

    @Test
    fun `valid chat creation succeeds`() = runTest {
        val chat = buildChat {}
        val validator = ChatValidatorFake()
        val repository = RepositoryFake()
        val useCase = CreateChatUseCase(chat, repository, validator)
        val result = useCase()
        assertIs<ResultWithError.Success<Chat, CreateChatError>>(result)
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
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(result)
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
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(result)
        assertEquals(repositoryError, result.error)
    }

    @Test
    fun `create second chat with same id fails`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val chat = buildChat { id = chatId }
        val validator = ChatValidatorFake()
        val repository = RepositoryFake()
        val result = CreateChatUseCase(chat, repository, validator)()
        assertIs<ResultWithError.Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)

        val newParticipant = buildParticipant { }
        val newChat = buildChat {
            id = chatId
            name = "Second Chat"
            participants = persistentSetOf(newParticipant)
        }
        val newResult = CreateChatUseCase(newChat, repository, validator)()
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(newResult)
        assertEquals(RepositoryCreateChatError.DuplicateChatId, newResult.error)
    }
}
