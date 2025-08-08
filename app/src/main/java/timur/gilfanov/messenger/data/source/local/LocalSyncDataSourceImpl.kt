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
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.data.source.local.datastore.SyncPreferences
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDeletedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.ChatUpdatedDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat

@Suppress("TooGenericExceptionCaught")
class LocalSyncDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val database: MessengerDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
) : LocalSyncDataSource {

    override suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError> =
        try {
            val preferences = dataStore.data.first()
            val timestamp = preferences[SyncPreferences.LAST_SYNC_TIMESTAMP]
            val instant = timestamp?.let { Instant.fromEpochMilliseconds(it) }
            ResultWithError.Success(instant)
        } catch (e: Exception) {
            ResultWithError.Failure(
                LocalDataSourceError.UnknownError(e),
            )
        }

    override suspend fun updateLastSyncTimestamp(
        timestamp: Instant,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        dataStore.edit { preferences ->
            preferences[SyncPreferences.LAST_SYNC_TIMESTAMP] = timestamp.toEpochMilliseconds()
        }
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        ResultWithError.Failure(
            LocalDataSourceError.UnknownError(e),
        )
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
        ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
    } catch (e: Exception) {
        ResultWithError.Failure(
            LocalDataSourceError.UnknownError(e),
        )
    }

    override suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError> {
        return try {
            // Apply all deltas in a single transaction for atomicity
            database.withTransaction {
                // Apply each delta in order
                delta.changes.forEach { chatDelta ->
                    when (chatDelta) {
                        is ChatCreatedDelta -> applyChatCreatedDelta(chatDelta)
                        is ChatUpdatedDelta -> applyChatUpdatedDelta(chatDelta)
                        is ChatDeletedDelta -> applyChatDeletedDelta(chatDelta)
                    }
                }
            }

            // Update sync timestamp on success (outside transaction for better performance)
            dataStore.edit { preferences ->
                preferences[SyncPreferences.LAST_SYNC_TIMESTAMP] =
                    delta.toTimestamp.toEpochMilliseconds()
            }

            ResultWithError.Success(Unit)
        } catch (e: SQLiteException) {
            // Don't update timestamp on database error - will retry from last successful sync
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
        } catch (e: Exception) {
            // Don't update timestamp on error - will retry from last successful sync
            ResultWithError.Failure(
                LocalDataSourceError.UnknownError(e),
            )
        }
    }

    private suspend fun applyChatCreatedDelta(delta: ChatCreatedDelta) {
        // Create a new chat from metadata
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

        // Insert chat entity
        val chatEntity = with(EntityMappers) { chat.toChatEntity() }
        chatDao.insertChat(chatEntity)

        // Insert participants
        val participantEntities = delta.chatMetadata.participants.map { participant ->
            with(EntityMappers) { participant.toParticipantEntity() }
        }
        participantDao.insertParticipants(participantEntities)

        // Insert chat-participant associations
        delta.chatMetadata.participants.forEach { participant ->
            chatDao.insertChatParticipantCrossRef(
                ChatParticipantCrossRef(
                    chatId = delta.chatId.id.toString(),
                    participantId = participant.id.id.toString(),
                ),
            )
        }

        // Insert initial messages
        delta.initialMessages.forEach { message ->
            val messageEntity = with(EntityMappers) { message.toMessageEntity() }
            messageDao.insertMessage(messageEntity)
        }
    }

    private suspend fun applyChatUpdatedDelta(delta: ChatUpdatedDelta) {
        // Update chat metadata
        val existingChatEntity = chatDao.getChatById(delta.chatId.id.toString())
        if (existingChatEntity != null) {
            val updatedChat = Chat(
                id = delta.chatId,
                name = delta.chatMetadata.name,
                participants = delta.chatMetadata.participants,
                pictureUrl = delta.chatMetadata.pictureUrl,
                messages = delta.messagesToAdd, // Include messages for proper entity creation
                rules = delta.chatMetadata.rules,
                unreadMessagesCount = delta.chatMetadata.unreadMessagesCount,
                lastReadMessageId = delta.chatMetadata.lastReadMessageId,
            )

            val updatedChatEntity = with(EntityMappers) { updatedChat.toChatEntity() }
            chatDao.updateChat(updatedChatEntity)

            // Update participants
            chatDao.removeAllChatParticipants(delta.chatId.id.toString())
            val participantEntities = delta.chatMetadata.participants.map { participant ->
                with(EntityMappers) { participant.toParticipantEntity() }
            }
            participantDao.insertParticipants(participantEntities)

            delta.chatMetadata.participants.forEach { participant ->
                chatDao.insertChatParticipantCrossRef(
                    ChatParticipantCrossRef(
                        chatId = delta.chatId.id.toString(),
                        participantId = participant.id.id.toString(),
                    ),
                )
            }

            // Add new messages
            delta.messagesToAdd.forEach { message ->
                val messageEntity = with(EntityMappers) { message.toMessageEntity() }
                messageDao.insertMessage(messageEntity)
            }

            // Delete removed messages
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
