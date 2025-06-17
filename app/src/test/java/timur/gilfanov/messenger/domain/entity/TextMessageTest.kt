package timur.gilfanov.messenger.domain.entity

import java.util.UUID
import junit.framework.TestCase.assertTrue
import kotlinx.datetime.Clock
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

@Category(timur.gilfanov.annotations.Unit::class)
class TextMessageTest {

    @Test
    fun validationIsWorking() {
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = null,
        )

        val message = TextMessage(
            id = MessageId(UUID.randomUUID()),
            parentId = MessageId(UUID.randomUUID()),
            sender = participant,
            recipient = ChatId(UUID.randomUUID()),
            createdAt = Clock.System.now(),
            text = "Valid text",
        )

        val validationResult = message.validate()
        assertTrue(validationResult.isSuccess)

        val validationResultEmpty = message.copy(text = "").validate()
        assertTrue(validationResultEmpty.isFailure)
    }
}
