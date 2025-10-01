package timur.gilfanov.messenger.test

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.paging.PagingData
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.data.repository.MessengerRepositoryImpl
import timur.gilfanov.messenger.data.source.local.LocalChatDataSourceImpl
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSourceImpl
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSourceImpl
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositoryEditMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError
import timur.gilfanov.messenger.test.AndroidTestDataHelper.bobChat
import timur.gilfanov.messenger.util.Logger

class AndroidTestRepositoryWithRealImplementation(
    private val dataScenario: AndroidTestDataHelper.DataScenario,
    private val logger: Logger = TestLogger(),
) : ChatRepository,
    MessageRepository,
    Closeable {

    private val instanceId = Integer.toHexString(System.identityHashCode(this))
    val remoteDataSourceFake = RemoteDataSourceFake(logger)

    companion object {
        private const val TAG = "AndroidTestRepo"
    }
    private val dataStoreScope: CoroutineScope
    private val repositoryScope: CoroutineScope
    private val realRepository: MessengerRepositoryImpl

    init {
        logger.d(TAG, "Creating repository instance: $instanceId for scenario: $dataScenario")

        val (dataScope, repoScope, repo) = run {
            val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

            // Create in-memory database
            val database = Room.inMemoryDatabaseBuilder(context, MessengerDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            // Create test DataStore with unique name to avoid conflicts
            val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dataScope,
                produceFile = {
                    context.preferencesDataStoreFile(
                        "android_test_preferences_${System.currentTimeMillis()}_" +
                            "${Thread.currentThread().hashCode()}",
                    )
                },
            )

            // Prepopulate database with test data based on scenario
            runBlocking {
                when (dataScenario) {
                    AndroidTestDataHelper.DataScenario.EMPTY -> {
                        // No prepopulation for empty scenario
                    }
                    AndroidTestDataHelper.DataScenario.NON_EMPTY -> {
                        AndroidTestDataHelper.prepopulateDatabase(database)
                        AndroidTestDataHelper.prepopulateRemoteDataSource(remoteDataSourceFake)
                    }
                }
            }

            val localDataSources = LocalDataSources(
                chat = LocalChatDataSourceImpl(
                    database = database,
                    chatDao = database.chatDao(),
                    participantDao = database.participantDao(),
                    logger = logger,
                ),
                message = LocalMessageDataSourceImpl(
                    database = database,
                    messageDao = database.messageDao(),
                    chatDao = database.chatDao(),
                    logger = logger,
                ),
                sync = LocalSyncDataSourceImpl(
                    dataStore = dataStore,
                    database = database,
                    chatDao = database.chatDao(),
                    messageDao = database.messageDao(),
                    participantDao = database.participantDao(),
                    logger = logger,
                ),
            )

            val remoteDataSources = RemoteDataSources(
                chat = remoteDataSourceFake,
                message = remoteDataSourceFake,
                sync = remoteDataSourceFake,
            )

            val repoScope = CoroutineScope(SupervisorJob())
            val repo = MessengerRepositoryImpl(
                localDataSources = localDataSources,
                remoteDataSources = remoteDataSources,
                logger = logger,
                backgroundScope = repoScope,
            )

            Triple(dataScope, repoScope, repo)
        }

        dataStoreScope = dataScope
        repositoryScope = repoScope
        realRepository = repo

        logger.d(TAG, "Repository instance $instanceId created successfully")
    }

    override fun close() {
        logger.d(TAG, "Closing repository instance: $instanceId")
        dataStoreScope.cancel()
        repositoryScope.cancel()
        logger.d(TAG, "Repository instance $instanceId closed")
    }

    @Suppress("deprecation")
    protected fun finalize() {
        logger.w(TAG, "Repository instance $instanceId is being garbage collected")
    }

    // ChatRepository implementation - delegate to real repository
    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError> =
        realRepository.createChat(chat)

    override suspend fun deleteChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryDeleteChatError> = realRepository.deleteChat(chatId)

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> = realRepository.joinChat(chatId, inviteLink)

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> = realRepository.leaveChat(chatId)

    override suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListError>,
        > =
        realRepository.flowChatList()

    override fun isChatListUpdating(): Flow<Boolean> = realRepository.isChatListUpdating()

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        realRepository.receiveChatUpdates(chatId)

    // MessageRepository implementation - delegate to real repository
    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositorySendMessageError>> =
        realRepository.sendMessage(message)

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositoryEditMessageError>> =
        realRepository.editMessage(message)

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> =
        realRepository.deleteMessage(messageId, mode)

    override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
        realRepository.getPagedMessages(chatId)

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RepositoryMarkMessagesAsReadError> =
        realRepository.markMessagesAsRead(chatId, upToMessageId)

    fun simulateBobSendingMessage(messageText: String, createdAt: Instant) {
        // Simulate Bob sending a new message
        val bobUser = AndroidTestDataHelper.bobUser

        val sender = DomainTestFixtures.createTestParticipant(
            id = bobUser.id,
            name = bobUser.name,
            joinedAt = bobUser.joinedAt,
        )

        val message = DomainTestFixtures.createTestTextMessage(
            sender = sender,
            recipient = bobChat.id,
            text = messageText,
            deliveryStatus = DeliveryStatus.Delivered,
            createdAt = createdAt,
        )

        // Add the message to Bob's chat
        remoteDataSourceFake.addMessage(message)
    }
}
