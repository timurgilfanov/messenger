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
        """SELECT COUNT(*) FROM messages 
           WHERE chatId = :chatId 
           AND createdAt > (SELECT createdAt FROM messages WHERE id = :lastReadMessageId)""",
    )
    suspend fun getUnreadMessageCount(chatId: String, lastReadMessageId: String): Int

    @Query(
        """SELECT * FROM messages 
           WHERE chatId = :chatId 
           AND createdAt < :beforeTimestamp
           ORDER BY createdAt DESC 
           LIMIT :limit""",
    )
    suspend fun getMessagesByChatIdPaged(
        chatId: String,
        beforeTimestamp: Long,
        limit: Int,
    ): List<MessageEntity>
}
