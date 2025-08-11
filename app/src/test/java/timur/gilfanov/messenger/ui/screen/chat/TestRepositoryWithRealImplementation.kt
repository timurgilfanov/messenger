package timur.gilfanov.messenger.ui.screen.chat

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.paging.PagingData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
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
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositoryEditMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError
import timur.gilfanov.messenger.util.NoOpLogger

class TestRepositoryWithRealImplementation :
    ChatRepository,
    MessageRepository {

    private val realRepository: MessengerRepositoryImpl by lazy {
        val context: Context = ApplicationProvider.getApplicationContext()

        // Create in-memory database
        val database = Room.inMemoryDatabaseBuilder(
            context,
            MessengerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        // Create test DataStore
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                context.preferencesDataStoreFile(
                    "test_chat_feature_preferences_${System.currentTimeMillis()}",
                )
            },
        )

        // Create and configure remote data source fake
        val remoteDataSourceFake = RemoteDataSourceFake()

        // Prepopulate database with test data
        runBlocking {
            ChatFeatureTestDataHelper.prepopulateDatabase(database)
            ChatFeatureTestDataHelper.prepopulateRemoteDataSource(remoteDataSourceFake)
        }

        val logger = NoOpLogger()

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

        MessengerRepositoryImpl(
            localDataSources = localDataSources,
            remoteDataSources = remoteDataSources,
            logger = logger,
            repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
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
}
