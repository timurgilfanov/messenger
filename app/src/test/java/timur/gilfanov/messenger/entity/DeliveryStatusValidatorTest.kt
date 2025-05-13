package timur.gilfanov.messenger.entity

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import timur.gilfanov.messenger.entity.model.message.DeliveryError
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus
import timur.gilfanov.messenger.entity.validation.DeliveryStatusValidator

class DeliveryStatusValidatorTest {

    private val validator = DeliveryStatusValidator()

    @Test
    fun deliveredToSentTransition() {
        val currentStatus = DeliveryStatus.Delivered
        val newStatus = DeliveryStatus.Sent
        val result = validator.validate(currentStatus, newStatus)
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception.message?.contains("Cannot change status from Delivered to Sent") == true,
        )
    }

    @Test
    fun deliveredToFailedTransition() {
        val currentStatus = DeliveryStatus.Delivered
        val deliveryError = DeliveryError.NetworkUnavailable
        val newStatus = DeliveryStatus.Failed(deliveryError)
        val result = validator.validate(currentStatus, newStatus)
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception.message?.contains("Cannot change status from Delivered to Failed") == true,
        )
    }

    @Test
    fun deliveredToSendingTransition() {
        val currentStatus = DeliveryStatus.Delivered
        val newStatus = DeliveryStatus.Sending(50)
        val result = validator.validate(currentStatus, newStatus)
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception.message?.contains("Cannot change status from Delivered to Sending") == true,
        )
    }

    @Test
    fun deliveredToDeliveredTransition() {
        val currentStatus = DeliveryStatus.Delivered
        val newStatus = DeliveryStatus.Delivered
        val result = validator.validate(currentStatus, newStatus)
        assertTrue(result.isSuccess)
    }

    @Test
    fun deliveredToReadTransition() {
        val currentStatus = DeliveryStatus.Delivered
        val newStatus = DeliveryStatus.Read
        val result = validator.validate(currentStatus, newStatus)
        assertTrue(result.isSuccess)
    }

    @Test
    fun sentToFailedTransition() {
        val currentStatus = DeliveryStatus.Sent
        val deliveryError = DeliveryError.ServerUnreachable
        val newStatus = DeliveryStatus.Failed(deliveryError)
        val result = validator.validate(currentStatus, newStatus)
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertTrue(exception.message?.contains("Cannot change status from Sent to Failed") == true)
    }

    @Test
    fun sentToSendingTransition() {
        val currentStatus = DeliveryStatus.Sent
        val newStatus = DeliveryStatus.Sending(75)
        val result = validator.validate(currentStatus, newStatus)
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertTrue(exception.message?.contains("Cannot change status from Sent to Sending") == true)
    }

    @Test
    fun sentToDeliveredTransition() {
        val currentStatus = DeliveryStatus.Sent
        val newStatus = DeliveryStatus.Delivered
        val result = validator.validate(currentStatus, newStatus)
        assertTrue(result.isSuccess)
    }

    @Test
    fun sentToSentTransition() {
        val currentStatus = DeliveryStatus.Sent
        val newStatus = DeliveryStatus.Sent
        val result = validator.validate(currentStatus, newStatus)
        assertTrue(result.isSuccess)
    }

    @Test
    fun sentToReadTransition() {
        val currentStatus = DeliveryStatus.Sent
        val newStatus = DeliveryStatus.Read
        val result = validator.validate(currentStatus, newStatus)
        assertTrue(result.isSuccess)
    }

    @Test
    fun sendingToAnyStatus() {
        val currentStatus = DeliveryStatus.Sending(25)
        assertTrue(validator.validate(currentStatus, DeliveryStatus.Sent).isSuccess)
        assertTrue(validator.validate(currentStatus, DeliveryStatus.Delivered).isSuccess)
        val deliveryError = DeliveryError.MessageTooLarge
        assertTrue(
            validator.validate(currentStatus, DeliveryStatus.Failed(deliveryError)).isSuccess,
        )
    }

    @Test
    fun sendingWithDifferentProgressToAnyStatus() {
        val currentStatus0 = DeliveryStatus.Sending(0)
        val currentStatus50 = DeliveryStatus.Sending(50)
        val currentStatus100 = DeliveryStatus.Sending(100)

        assertTrue(validator.validate(currentStatus0, DeliveryStatus.Sent).isSuccess)
        assertTrue(validator.validate(currentStatus50, DeliveryStatus.Sent).isSuccess)
        assertTrue(validator.validate(currentStatus100, DeliveryStatus.Sent).isSuccess)

        val deliveryError = DeliveryError.RecipientBlocked
        val failedStatus = DeliveryStatus.Failed(deliveryError)
        assertTrue(validator.validate(currentStatus0, failedStatus).isSuccess)
        assertTrue(validator.validate(currentStatus50, failedStatus).isSuccess)
        assertTrue(validator.validate(currentStatus100, failedStatus).isSuccess)
    }

    @Test
    fun failedToAnyStatus() {
        val deliveryError = DeliveryError.RecipientNotFound
        val currentStatus = DeliveryStatus.Failed(deliveryError)
        assertTrue(validator.validate(currentStatus, DeliveryStatus.Sent).isSuccess)
        assertTrue(validator.validate(currentStatus, DeliveryStatus.Delivered).isSuccess)
        val anotherError = DeliveryError.MessageExpired
        assertTrue(validator.validate(currentStatus, DeliveryStatus.Failed(anotherError)).isSuccess)
    }

    @Test
    fun readToAnyStatus() {
        val currentStatus = DeliveryStatus.Read

        val sendingStatus = DeliveryStatus.Sending(30)
        val result1 = validator.validate(currentStatus, sendingStatus)
        assertFalse(result1.isSuccess)
        val exception1 = result1.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception1.message?.contains("Cannot change delivery status once it's Read") == true,
        )

        val sentStatus = DeliveryStatus.Sent
        val result2 = validator.validate(currentStatus, sentStatus)
        assertFalse(result2.isSuccess)
        val exception2 = result2.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception2.message?.contains("Cannot change delivery status once it's Read") == true,
        )

        val deliveredStatus = DeliveryStatus.Delivered
        val result3 = validator.validate(currentStatus, deliveredStatus)
        assertFalse(result3.isSuccess)
        val exception3 = result3.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception3.message?.contains("Cannot change delivery status once it's Read") == true,
        )

        val deliveryError = DeliveryError.RateLimitExceeded(retryAfter = 60.seconds)
        val failedStatus = DeliveryStatus.Failed(deliveryError)
        val result4 = validator.validate(currentStatus, failedStatus)
        assertFalse(result4.isSuccess)
        val exception4 = result4.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception4.message?.contains("Cannot change delivery status once it's Read") == true,
        )

        val readStatus = DeliveryStatus.Read
        val result5 = validator.validate(currentStatus, readStatus)
        assertFalse(result5.isSuccess)
        val exception5 = result5.exceptionOrNull() as IllegalStateException
        assertTrue(
            exception5.message?.contains("Cannot change delivery status once it's Read") == true,
        )
    }
}
