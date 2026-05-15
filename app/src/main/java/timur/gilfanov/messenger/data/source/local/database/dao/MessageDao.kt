package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity

/**
 * Data Access Object for message-related database operations.
 */
@Dao
@Suppress("TooManyFunctions") // DAO interfaces naturally have many functions
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessagesByChatId(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun flowMessagesByChatId(chatId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesInChat(chatId: String)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageDeliveryStatus(messageId: String, status: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessageInChat(chatId: String): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE chatId = :chatId AND senderId = :senderId " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    fun flowLastMessageBySenderInChat(chatId: String, senderId: String): Flow<MessageEntity?>

    @Query(
        "SELECT * FROM messages WHERE chatId = :chatId " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    fun flowLastMessageInChat(chatId: String): Flow<MessageEntity?>

    @Query(
        """SELECT COUNT(*) FROM messages 
           WHERE chatId = :chatId 
           AND createdAt > (SELECT createdAt FROM messages WHERE id = :lastReadMessageId)""",
    )
    suspend fun getUnreadMessageCount(chatId: String, lastReadMessageId: String): Int

    @Query(
        """SELECT * FROM messages
           WHERE chatId = :chatId
           AND (createdAt < :beforeTimestamp OR (createdAt = :beforeTimestamp AND id < :beforeMessageId))
           ORDER BY createdAt DESC, id DESC
           LIMIT :limit""",
    )
    suspend fun getMessagesByChatIdPaged(
        chatId: String,
        beforeTimestamp: Long,
        beforeMessageId: String,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """SELECT * FROM messages
           WHERE chatId = :chatId
           AND (createdAt < :anchorTimestamp OR (createdAt = :anchorTimestamp AND id <= :anchorId))
           ORDER BY createdAt DESC, id DESC
           LIMIT :limit""",
    )
    suspend fun getMessagesByChatIdPagedFromAnchor(
        chatId: String,
        anchorTimestamp: Long,
        anchorId: String,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """SELECT * FROM messages
           WHERE chatId = :chatId
           AND (createdAt > :afterTimestamp OR (createdAt = :afterTimestamp AND id > :afterMessageId))
           ORDER BY createdAt ASC, id ASC
           LIMIT :limit""",
    )
    suspend fun getMessagesByChatIdPagedNewerThan(
        chatId: String,
        afterTimestamp: Long,
        afterMessageId: String,
        limit: Int,
    ): List<MessageEntity>
}
