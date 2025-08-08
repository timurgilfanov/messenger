package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Data class representing a chat with its participants and messages.
 * Used by Room to fetch related data in a single query.
 */
data class ChatWithParticipantsAndMessages(
    @Embedded val chat: ChatEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ChatParticipantCrossRef::class,
            parentColumn = "chatId",
            entityColumn = "participantId",
        ),
    )
    val participants: List<ParticipantEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "chatId",
    )
    val messages: List<MessageEntity>,
)

