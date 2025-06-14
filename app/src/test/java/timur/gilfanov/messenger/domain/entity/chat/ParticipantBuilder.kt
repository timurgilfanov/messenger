package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.datetime.Instant

fun buildParticipant(builder: ParticipantBuilder.() -> Unit): Participant =
    ParticipantBuilder().apply(builder).build()

class ParticipantBuilder {
    var id: ParticipantId = ParticipantId(UUID.randomUUID())
    var name: String = "User"
    var pictureUrl: String? = null
    var joinedAt: Instant = Instant.fromEpochMilliseconds(0)
    var onlineAt: Instant? = null
    var isAdmin: Boolean = false
    var isModerator: Boolean = false

    fun build(): Participant = Participant(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        joinedAt = joinedAt,
        onlineAt = onlineAt,
        isAdmin = isAdmin,
        isModerator = isModerator,
    )
}
