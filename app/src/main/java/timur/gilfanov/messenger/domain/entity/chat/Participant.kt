package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlin.time.Instant

@JvmInline
value class ParticipantId(val id: UUID)

data class Participant(
    val id: ParticipantId,
    val name: String,
    val pictureUrl: String?,
    val joinedAt: Instant,
    val onlineAt: Instant?,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
) {
    override fun toString(): String = name
}
