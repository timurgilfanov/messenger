package timur.gilfanov.messenger.domain.usecase.privileged

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError.ChatIsNotValid
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.CreateChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class CreateChatUseCaseTest {

    private class RepositoryFake(val repoError: CreateChatRepositoryError? = null) :
        ChatRepository {
        val chats = mutableSetOf<Chat>()

        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, CreateChatRepositoryError> {
            repoError?.let {
                return ResultWithError.Failure(it)
            }
            return if (chats.any { it.id == chat.id }) {
                ResultWithError.Failure(CreateChatRepositoryError.DuplicateChatId)
            } else {
                chats.add(chat)
                ResultWithError.Success(chat)
            }
        }

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdateApplying() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = error("Not implemented")
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
        val useCase = CreateChatUseCase(repository, validator)
        val result = useCase(chat)
        assertIs<ResultWithError.Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)
    }

    @Test
    fun `invalid chat validation fails`() = runTest {
        val chat = buildChat {}
        val validationError = ChatValidationError.EmptyName
        val validator = ChatValidatorFake(validationError)
        val repository = RepositoryFake()
        val useCase = CreateChatUseCase(repository, validator)
        val result = useCase(chat)
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(result)
        assertIs<ChatIsNotValid>(result.error)
        assertEquals(validationError, result.error.error)
    }

    @Test
    fun `repository error handling`() = runTest {
        val validator = ChatValidatorFake()
        val chat = buildChat {}
        val repositoryError = CreateChatRepositoryError.RemoteOperationFailed(
            RemoteError.Unauthenticated,
        )
        val repository = RepositoryFake(repositoryError)
        val useCase = CreateChatUseCase(repository, validator)
        val result = useCase(chat)
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(result)
        assertIs<CreateChatError.RemoteOperationFailed>(result.error)
    }

    @Test
    fun `create second chat with same id fails`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val chat = buildChat { id = chatId }
        val validator = ChatValidatorFake()
        val repository = RepositoryFake()
        val useCase = CreateChatUseCase(repository, validator)
        val result = useCase(chat)
        assertIs<ResultWithError.Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)

        val newParticipant = buildParticipant { }
        val newChat = buildChat {
            id = chatId
            name = "Second Chat"
            participants = persistentSetOf(newParticipant)
        }
        val newResult = useCase(newChat)
        assertIs<ResultWithError.Failure<Chat, CreateChatError>>(newResult)
        assertEquals(CreateChatError.DuplicateChatId, newResult.error)
    }
}
