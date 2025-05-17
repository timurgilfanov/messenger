package timur.gilfanov.messenger.domain.entity

import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.message.TextMessage

class TextMessageTest {

    @Test
    fun validationIsWorking() {
        val message = TextMessage(
            id = mockk(),
            parentId = mockk(),
            sender = mockk(),
            recipient = mockk(),
            createdAt = mockk(),
            text = "Valid text",
        )

        val validationResult = message.validate()
        assertTrue(validationResult.isSuccess)

        val validationResultEmpty = message.copy(text = "").validate()
        assertTrue(validationResultEmpty.isFailure)
    }
}
