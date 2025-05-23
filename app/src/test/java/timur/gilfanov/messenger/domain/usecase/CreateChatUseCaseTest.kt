package timur.gilfanov.messenger.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.usecase.CreateChatError.ChatIsNotValid
import timur.gilfanov.messenger.domain.usecase.CreateChatError.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.RepositoryError.NetworkError
import timur.gilfanov.messenger.domain.usecase.RepositoryError.ServerError

class CreateChatUseCaseTest {

    @Test
    fun `valid chat creation succeeds`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Success(Unit)

        val repository = mockk<Repository>()
        coEvery { repository.createChat(chat) } returns Success(chat)

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Success<Chat, CreateChatError>>(result)
        assertEquals(chat, result.data)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 1) { repository.createChat(chat) }
    }

    @Test
    fun `invalid chat validation fails`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validationError = mockk<ChatValidationError.NoParticipants>()
        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Failure(validationError)

        val repository = mockk<Repository>()

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<ChatIsNotValid>(result.error)
        assertEquals(validationError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 0) { repository.createChat(any()) }
    }

    @Test
    fun `repository error handling`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()
        val validator = mockk<ChatValidator>()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        every { validator.validateOnCreation(chat) } returns Success(Unit)

        val repositoryError = RepositoryError.UnknownError
        val repository = mockk<Repository>()
        coEvery { repository.createChat(chat) } returns Failure(repositoryError)

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<RepositoryCreateChatError>(result.error)
        assertEquals(repositoryError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 1) { repository.createChat(chat) }
    }

    @Test
    fun `empty participants list validation`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validationError = mockk<ChatValidationError.NoParticipants>()
        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Failure(validationError)

        val repository = mockk<Repository>()

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<ChatIsNotValid>(result.error)
        assertEquals(validationError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 0) { repository.createChat(any()) }
    }

    @Test
    fun `empty chat name validation`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validationError = mockk<ChatValidationError.NoParticipants>()
        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Failure(validationError)

        val repository = mockk<Repository>()

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<ChatIsNotValid>(result.error)
        assertEquals(validationError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 0) { repository.createChat(any()) }
    }

    @Test
    fun `repository returns success`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val createdChat = chat.copy(name = "Updated Chat Name")

        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Success(Unit)

        val repository = mockk<Repository>()
        coEvery { repository.createChat(chat) } returns Success(createdChat)

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Success<Chat, CreateChatError>>(result)
        assertEquals(createdChat, result.data)
        assertEquals("Updated Chat Name", result.data.name)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 1) { repository.createChat(chat) }
    }

    @Test
    fun `repository returns network error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Success(Unit)

        val repository = mockk<Repository>()
        coEvery { repository.createChat(chat) } returns Failure(NetworkError)

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<RepositoryCreateChatError>(result.error)
        assertEquals(NetworkError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 1) { repository.createChat(chat) }
    }

    @Test
    fun `repository returns server error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val participant = createParticipant()

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validator = mockk<ChatValidator>()
        every { validator.validateOnCreation(chat) } returns Success(Unit)

        val repository = mockk<Repository>()
        coEvery { repository.createChat(chat) } returns Failure(ServerError)

        val useCase = CreateChatUseCase(chat, repository, validator)

        // Act
        val result = useCase()

        // Assert
        assertIs<Failure<Chat, CreateChatError>>(result)
        assertIs<RepositoryCreateChatError>(result.error)
        assertEquals(ServerError, result.error.error)

        verify { validator.validateOnCreation(chat) }
        coVerify(exactly = 1) { repository.createChat(chat) }
    }

    private fun createParticipant(): Participant = Participant(
        id = ParticipantId(UUID.randomUUID()),
        name = "Test User",
        pictureUrl = null,
        joinedAt = Clock.System.now(),
    )
}
