package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Room entity representing a message in the database.
 * Maps to the Message domain model.
 */
@Entity(
    tableName = "messages",
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
            childColumns = ["senderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["senderId"]),
        Index(value = ["createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val senderId: String,
    val parentId: String?, // For reply messages
    val type: MessageType,
    val text: String?, // For text messages
    val imageUrl: String?, // For image messages
    val deliveryStatus: String?, // Serialized DeliveryStatus
    val createdAt: Instant,
    val sentAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val editedAt: Instant? = null,
)

enum class MessageType {
    TEXT,
    IMAGE,
}
