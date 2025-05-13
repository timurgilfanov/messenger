package timur.gilfanov.messenger.entity

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.entity.model.chat.ChatUser
import timur.gilfanov.messenger.entity.model.message.DeliveryError
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus
import timur.gilfanov.messenger.entity.model.message.TextMessage

class TextMessageTest {

    @Test
    fun createTextMessageInstance() {
        val messageId = UUID.randomUUID()
        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)
        val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val text = "Hello, world!"

        val message = TextMessage(
            id = messageId,
            sender = sender,
            recipient = recipient,
            sentAt = sentAt,
            deliveryStatusInitialValue = DeliveryStatus.Sent,
            textInitialValue = text,
        )

        assertEquals(messageId, message.id)
        assertEquals(DeliveryStatus.Sent, message.deliveryStatus)
        assertEquals(sender, message.sender)
        assertEquals(recipient, message.recipient)
        assertEquals(sentAt, message.sentAt)
        assertEquals(text, message.text)
    }

    @Test
    fun updateTextProperty() {
        val messageId = UUID.randomUUID()
        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)
        val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val initialText = "Initial message"

        val message = TextMessage(
            id = messageId,
            sender = sender,
            recipient = recipient,
            sentAt = sentAt,
            deliveryStatusInitialValue = DeliveryStatus.Sent,
            textInitialValue = initialText,
        )

        assertEquals(initialText, message.text)

        val updatedText = "Updated message text"
        message.text = updatedText

        assertEquals(updatedText, message.text)
    }

    @Test
    fun updateTextToInvalidValue() {
        val messageId = UUID.randomUUID()
        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)
        val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val initialText = "Initial message"

        val message = TextMessage(
            id = messageId,
            sender = sender,
            recipient = recipient,
            sentAt = sentAt,
            deliveryStatusInitialValue = DeliveryStatus.Sent,
            textInitialValue = initialText,
        )

        val emptyText = ""
        assertFailsWith<IllegalArgumentException> {
            message.text = emptyText
        }

        assertEquals(
            initialText,
            message.text,
            "Original text should remain unchanged after failed updates",
        )
    }

    @Test
    fun deliveryStatusValidation() {
        val messageId = UUID.randomUUID()
        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)
        val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val text = "Test message"

        val message = TextMessage(
            id = messageId,
            sender = sender,
            recipient = recipient,
            sentAt = sentAt,
            deliveryStatusInitialValue = DeliveryStatus.Sent,
            textInitialValue = text,
        )

        // Update to Delivered should work
        message.deliveryStatus = DeliveryStatus.Delivered
        assertEquals(DeliveryStatus.Delivered, message.deliveryStatus)

        // Cannot change status after it's Delivered
        assertFailsWith<IllegalStateException> {
            message.deliveryStatus = DeliveryStatus.Sent
        }

        // Status should remain unchanged after failed update
        assertEquals(DeliveryStatus.Delivered, message.deliveryStatus)
    }

    @Test
    fun cannotChangeFromSentToFailed() {
        val messageId = UUID.randomUUID()
        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)
        val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val text = "Test message"

        val message = TextMessage(
            id = messageId,
            sender = sender,
            recipient = recipient,
            sentAt = sentAt,
            deliveryStatusInitialValue = DeliveryStatus.Sent,
            textInitialValue = text,
        )

        // Cannot change from Sent to Failed
        val failedStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)
        assertFailsWith<IllegalStateException> {
            message.deliveryStatus = failedStatus
        }

        // Status should remain unchanged after failed update
        assertEquals(DeliveryStatus.Sent, message.deliveryStatus)
    }
}
