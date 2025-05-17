package timur.gilfanov.messenger.domain.entity.communication

import java.util.UUID

interface Participant {
    val id: UUID
    val name: String
    val pictureUrl: String?
}
