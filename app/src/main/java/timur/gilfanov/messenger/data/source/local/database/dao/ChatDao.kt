package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages

/**
 * Data Access Object for chat-related database operations.
 */
@Dao
@Suppress("TooManyFunctions") // DAO interfaces naturally have many functions
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Transaction
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatWithParticipantsAndMessages(chatId: String): ChatWithParticipantsAndMessages?

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun flowAllChats(): Flow<List<ChatEntity>>

    @Transaction
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun flowAllChatsWithParticipantsAndMessages(): Flow<List<ChatWithParticipantsAndMessages>>

    @Transaction
    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun flowChatWithParticipantsAndMessages(chatId: String): Flow<ChatWithParticipantsAndMessages?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatParticipantCrossRef(crossRef: ChatParticipantCrossRef)

    @Query(
        "DELETE FROM chat_participants WHERE chatId = :chatId AND participantId = :participantId",
    )
    suspend fun removeChatParticipant(chatId: String, participantId: String)

    @Query("DELETE FROM chat_participants WHERE chatId = :chatId")
    suspend fun removeAllChatParticipants(chatId: String)
}
