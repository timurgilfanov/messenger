package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.datetime.Instant

@JvmInline
value class ParticipantId(val id: UUID)

data class Participant(
    val id: ParticipantId,
    val name: String,
    val pictureUrl: String?,
    val joinedAt: Instant,
)
