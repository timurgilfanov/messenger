package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.data.source.local.datastore.SyncPreferences
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDeletedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.ChatUpdatedDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.util.Logger

class LocalSyncDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val database: MessengerDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
    private val logger: Logger,
) : LocalSyncDataSource {

    private val errorHandler = DatabaseErrorHandler(logger)

    override suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError> =
        try {
            val preferences = dataStore.data.first()
            val timestamp = preferences[SyncPreferences.LAST_SYNC_TIMESTAMP]
            val instant = timestamp?.let { Instant.fromEpochMilliseconds(it) }
            ResultWithError.Success(instant)
        } catch (@Suppress("SwallowedException") e: androidx.datastore.core.IOException) {
            ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
        }

    override suspend fun updateLastSyncTimestamp(
        timestamp: Instant,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        dataStore.edit { preferences ->
            preferences[SyncPreferences.LAST_SYNC_TIMESTAMP] = timestamp.toEpochMilliseconds()
        }
        ResultWithError.Success(Unit)
    } catch (@Suppress("SwallowedException") e: androidx.datastore.core.IOException) {
        ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
    }

    override suspend fun applyChatDelta(
        delta: ChatDelta,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        database.withTransaction {
            when (delta) {
                is ChatCreatedDelta -> applyChatCreatedDelta(delta)
                is ChatUpdatedDelta -> applyChatUpdatedDelta(delta)
                is ChatDeletedDelta -> applyChatDeletedDelta(delta)
            }
        }
        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    }

    override suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        database.withTransaction {
            delta.changes.forEach { chatDelta ->
                when (chatDelta) {
                    is ChatCreatedDelta -> applyChatCreatedDelta(chatDelta)
                    is ChatUpdatedDelta -> applyChatUpdatedDelta(chatDelta)
                    is ChatDeletedDelta -> applyChatDeletedDelta(chatDelta)
                }
            }
        }

        dataStore.edit { preferences ->
            preferences[SyncPreferences.LAST_SYNC_TIMESTAMP] =
                delta.toTimestamp.toEpochMilliseconds()
        }

        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    } catch (@Suppress("SwallowedException") e: androidx.datastore.core.IOException) {
        ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
    }

    private suspend fun applyChatCreatedDelta(delta: ChatCreatedDelta) {
        val chat = Chat(
            id = delta.chatId,
            name = delta.chatMetadata.name,
            participants = delta.chatMetadata.participants,
            pictureUrl = delta.chatMetadata.pictureUrl,
            messages = delta.initialMessages,
            rules = delta.chatMetadata.rules,
            unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
            lastReadMessageId = delta.chatMetadata.lastReadMessageId,
        )

        val chatEntity = with(EntityMappers) { chat.toChatEntity() }
        chatDao.insertChat(chatEntity)

        // Insert global participant identities
        val participantEntities = delta.chatMetadata.participants.map { participant ->
            with(EntityMappers) { participant.toParticipantEntity() }
        }
        participantDao.insertParticipants(participantEntities)

        // Insert chat-specific participant relationships
        val crossRefs = delta.chatMetadata.participants.map { participant ->
            with(EntityMappers) {
                participant.toChatParticipantCrossRef(delta.chatId.id.toString())
            }
        }
        chatDao.insertChatParticipantCrossRefs(crossRefs)

        delta.initialMessages.forEach { message ->
            val messageEntity = with(EntityMappers) { message.toMessageEntity() }
            messageDao.insertMessage(messageEntity)
        }
    }

    private suspend fun applyChatUpdatedDelta(delta: ChatUpdatedDelta) {
        val existingChatEntity = chatDao.getChatById(delta.chatId.id.toString())
        if (existingChatEntity != null) {
            val updatedChat = Chat(
                id = delta.chatId,
                name = delta.chatMetadata.name,
                participants = delta.chatMetadata.participants,
                pictureUrl = delta.chatMetadata.pictureUrl,
                messages = delta.messagesToAdd,
                rules = delta.chatMetadata.rules,
                unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                lastReadMessageId = delta.chatMetadata.lastReadMessageId,
            )

            val updatedChatEntity = with(EntityMappers) { updatedChat.toChatEntity() }
            chatDao.updateChat(updatedChatEntity)

            chatDao.removeAllChatParticipants(delta.chatId.id.toString())

            // Insert/update global participant identities
            val participantEntities = delta.chatMetadata.participants.map { participant ->
                with(EntityMappers) { participant.toParticipantEntity() }
            }
            participantDao.insertParticipants(participantEntities)

            // Insert new chat-specific participant relationships
            val crossRefs = delta.chatMetadata.participants.map { participant ->
                with(EntityMappers) {
                    participant.toChatParticipantCrossRef(delta.chatId.id.toString())
                }
            }
            chatDao.insertChatParticipantCrossRefs(crossRefs)

            delta.messagesToAdd.forEach { message ->
                val messageEntity = with(EntityMappers) { message.toMessageEntity() }
                messageDao.insertMessage(messageEntity)
            }

            delta.messagesToDelete.forEach { messageId ->
                val messageEntity = messageDao.getMessageById(messageId.id.toString())
                messageEntity?.let { messageDao.deleteMessage(it) }
            }
        }
    }

    private suspend fun applyChatDeletedDelta(delta: ChatDeletedDelta) {
        val chatEntity = chatDao.getChatById(delta.chatId.id.toString())
        chatEntity?.let { chatDao.deleteChat(it) }
    }
}
