package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * Room entity representing a chat in the database.
 * Maps to the Chat domain model.
 */
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val pictureUrl: String?,
    val rules: String, // JSON serialized set of rules
    val unreadMessagesCount: Int,
    val lastReadMessageId: String?,
    val updatedAt: Instant?,
)
