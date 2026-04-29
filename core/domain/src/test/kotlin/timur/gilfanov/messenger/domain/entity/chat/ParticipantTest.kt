package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit

@Category(Unit::class)
class ParticipantTest {

    private val baseInstant = Instant.fromEpochMilliseconds(1000)

    @Test
    fun `isCurrentUser defaults to false`() {
        val participant = Participant(
            id = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            name = "Alice",
            pictureUrl = null,
            joinedAt = baseInstant,
            onlineAt = null,
        )

        assertFalse(participant.isCurrentUser)
    }

    @Test
    fun `isCurrentUser can be set to true`() {
        val participant = Participant(
            id = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            name = "Alice",
            pictureUrl = null,
            joinedAt = baseInstant,
            onlineAt = null,
            isCurrentUser = true,
        )

        assertTrue(participant.isCurrentUser)
    }

    @Test
    fun `copy preserves isCurrentUser`() {
        val original = Participant(
            id = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            name = "Alice",
            pictureUrl = null,
            joinedAt = baseInstant,
            onlineAt = null,
            isCurrentUser = true,
        )

        val copy = original.copy(name = "Alice Updated")

        assertTrue(copy.isCurrentUser)
    }

    @Test
    fun `copy can override isCurrentUser`() {
        val original = Participant(
            id = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            name = "Alice",
            pictureUrl = null,
            joinedAt = baseInstant,
            onlineAt = null,
            isCurrentUser = true,
        )

        val copy = original.copy(isCurrentUser = false)

        assertFalse(copy.isCurrentUser)
    }
}
