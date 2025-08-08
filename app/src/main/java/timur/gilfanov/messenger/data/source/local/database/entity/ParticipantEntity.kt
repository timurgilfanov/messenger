package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Room entity representing a participant in the database.
 * Maps to the Participant domain model.
 */
@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val pictureUrl: String?,
    val joinedAt: Instant,
    val onlineAt: Instant?,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
)
