package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Room entity representing a participant's global identity in the database.
 * Chat-specific properties (joinedAt, isAdmin, isModerator) are stored in ChatParticipantCrossRef.
 */
@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val pictureUrl: String?,
    val onlineAt: Instant?,
)
