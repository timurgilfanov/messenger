package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.datetime.Instant

/**
 * Junction table for many-to-many relationship between chats and participants.
 * Contains chat-specific participant properties.
 */
@Entity(
    tableName = "chat_participants",
    primaryKeys = ["chatId", "participantId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["participantId"]),
    ],
)
data class ChatParticipantCrossRef(
    val chatId: String,
    val participantId: String,
    val joinedAt: Instant,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
)
