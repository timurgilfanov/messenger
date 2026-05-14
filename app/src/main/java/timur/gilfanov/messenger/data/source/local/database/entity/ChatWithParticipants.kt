package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Data class representing a chat with its participants only (without messages).
 * Used for efficient lookups that need participant data but not the full message history.
 */
data class ChatWithParticipants(
    @Embedded val chat: ChatEntity,
    @Relation(
        entity = ChatParticipantCrossRef::class,
        parentColumn = "id",
        entityColumn = "chatId",
    )
    val participantCrossRefs: List<ChatParticipantCrossRef>,
    @Relation(
        entity = ParticipantEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ChatParticipantCrossRef::class,
            parentColumn = "chatId",
            entityColumn = "participantId",
        ),
    )
    val participants: List<ParticipantEntity>,
)
