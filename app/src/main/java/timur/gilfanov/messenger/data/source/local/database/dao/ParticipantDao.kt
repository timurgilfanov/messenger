package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity

/**
 * Data Access Object for participant-related database operations.
 */
@Dao
interface ParticipantDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipant(participant: ParticipantEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipants(participants: List<ParticipantEntity>)

    @Update
    suspend fun updateParticipant(participant: ParticipantEntity)

    @Query("UPDATE participants SET onlineAt = :timestamp WHERE id = :participantId")
    suspend fun updateParticipantOnlineStatus(participantId: String, timestamp: Long?)

    @Delete
    suspend fun deleteParticipant(participant: ParticipantEntity)

    @Query("DELETE FROM participants WHERE id = :participantId")
    suspend fun deleteParticipantById(participantId: String)

    @Query("SELECT * FROM participants WHERE id = :participantId")
    suspend fun getParticipantById(participantId: String): ParticipantEntity?

    @Query("SELECT * FROM participants")
    suspend fun getAllParticipants(): List<ParticipantEntity>

    @Query(
        """
        SELECT p.* FROM participants p
        INNER JOIN chat_participants cp ON p.id = cp.participantId
        WHERE cp.chatId = :chatId
    """,
    )
    suspend fun getParticipantsByChatId(chatId: String): List<ParticipantEntity>
}
