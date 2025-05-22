package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.datetime.Instant

data class Participant(
    val id: UUID, // TODO Use inline class for compile time type safety check
    val name: String,
    val pictureUrl: String?,
    val joinedAt: Instant,
)
