package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlin.time.Instant

/**
 * Unique identifier for a participant in the Chat/Message bounded context.
 *
 * Represents a user's identity within chat conversations.
 * Used for message authorship, chat membership, and permissions.
 *
 * Note: This is intentionally separate from [timur.gilfanov.messenger.domain.entity.profile.UserId] to:
 * - Maintain bounded context separation (DDD principle)
 * - Protect user privacy (ParticipantId should not be matchable to UserId)
 * - Support future extensibility (anonymous participants, bots, external users)
 *
 * @see timur.gilfanov.messenger.domain.entity.profile.UserId
 */
@JvmInline
value class ParticipantId(val id: UUID)

fun String.toParticipantId(): ParticipantId = ParticipantId(UUID.fromString(this))

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
