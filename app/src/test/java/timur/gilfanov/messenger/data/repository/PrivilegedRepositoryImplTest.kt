package timur.gilfanov.messenger.data.repository

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryDeleteChatError

@Category(Unit::class)
class PrivilegedRepositoryImplTest {

    private lateinit var localDataSource: LocalDataSourceFake
    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var repository: PrivilegedRepositoryImpl

    private lateinit var testChat: Chat
    private lateinit var testParticipant: Participant

    @Before
    fun setup() {
        localDataSource = LocalDataSourceFake()
        remoteDataSource = RemoteDataSourceFake()
        repository = PrivilegedRepositoryImpl(localDataSource, remoteDataSource)

        testParticipant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )

        val chatId = ChatId(UUID.randomUUID())
        val testMessage = TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = chatId,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Delivered,
        )

        testChat = Chat(
            id = chatId,
            participants = persistentSetOf(testParticipant),
            name = "Test Chat",
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(testMessage),
        )
    }

    @Test
    fun `createChat should create chat on remote and store locally`() = runTest {
        // When: Create chat
        val result = repository.createChat(testChat)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Chat, RepositoryCreateChatError>>(result)
        assertEquals(testChat.id, result.data.id)

        // Verify chat was stored locally
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, *>>(localChat)
        assertEquals(testChat.id, localChat.data.id)
    }

    @Test
    fun `createChat should return error when network unavailable`() = runTest {
        // Given: Network is disconnected
        remoteDataSource.setConnectionState(false)

        // When: Try to create chat
        val result = repository.createChat(testChat)

        // Then: Should return network error
        assertIs<ResultWithError.Failure<Chat, RepositoryCreateChatError>>(result)
        assertEquals(RepositoryCreateChatError.NetworkNotAvailable, result.error)

        // Verify chat was not stored locally
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Failure<Chat, *>>(localChat)
    }

    @Test
    fun `deleteChat should delete from remote and local`() = runTest {
        // Given: Chat exists in both sources
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)

        // When: Delete chat
        val result = repository.deleteChat(testChat.id)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Unit, RepositoryDeleteChatError>>(result)

        // Verify chat was removed from local storage
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Failure<Chat, *>>(localChat)
    }

    @Test
    fun `deleteChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.randomUUID())

        // When: Try to delete non-existent chat
        val result = repository.deleteChat(nonExistentChatId)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteChatError>>(result)
        assertEquals(RepositoryDeleteChatError.ChatNotFound(nonExistentChatId), result.error)
    }

    @Test
    fun `deleteChat should return error when network unavailable`() = runTest {
        // Given: Chat exists locally but network is disconnected
        localDataSource.insertChat(testChat)
        remoteDataSource.setConnectionState(false)

        // When: Try to delete chat
        val result = repository.deleteChat(testChat.id)

        // Then: Should return network error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteChatError>>(result)
        assertEquals(RepositoryDeleteChatError.NetworkNotAvailable, result.error)

        // Verify chat still exists locally (remote-first approach failed)
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, *>>(localChat)
    }
}
