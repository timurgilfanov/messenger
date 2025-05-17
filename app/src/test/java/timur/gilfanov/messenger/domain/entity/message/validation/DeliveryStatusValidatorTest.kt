package timur.gilfanov.messenger.domain.entity.message.validation

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Failed
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Read
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sent
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToFailed
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToSending
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToSent
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToUndefined
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromRead
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromUndefinedToOtherThanSending

class DeliveryStatusValidatorTest {

    private val validator = DeliveryStatusValidator()

    @Test
    fun validateNullToSendingTransition() {
        val result = validator.validate(
            currentStatus = null,
            newStatus = Sending(50),
        )
        assertTrue(result is Success)
    }

    @Test
    fun validateSendingToAnyTransition() {
        val sendingStatus = Sending(50)

        // Test transition to Sent
        var result = validator.validate(
            currentStatus = sendingStatus,
            newStatus = Sent,
        )
        assertTrue(result is Success)

        // Test transition to Failed
        result = validator.validate(
            currentStatus = sendingStatus,
            newStatus = Failed(DeliveryError.NetworkUnavailable),
        )
        assertTrue(result is Success)

        // Test transition to another Sending state
        result = validator.validate(
            currentStatus = sendingStatus,
            newStatus = Sending(75),
        )
        assertTrue(result is Success)

        // Test transition to null
        result = validator.validate(
            currentStatus = sendingStatus,
            newStatus = null,
        )
        assertTrue(result is Success)
    }

    @Test
    fun validateFailedToAnyTransition() {
        val failedStatus = Failed(DeliveryError.NetworkUnavailable)

        // Test transition to Sending
        var result = validator.validate(
            currentStatus = failedStatus,
            newStatus = Sending(25),
        )
        assertTrue(result is Success)

        // Test transition to Sent
        result = validator.validate(
            currentStatus = failedStatus,
            newStatus = Sent,
        )
        assertTrue(result is Success)

        // Test transition to another Failed state
        result = validator.validate(
            currentStatus = failedStatus,
            newStatus = Failed(DeliveryError.ServerUnreachable),
        )
        assertTrue(result is Success)

        // Test transition to null
        result = validator.validate(
            currentStatus = failedStatus,
            newStatus = null,
        )
        assertTrue(result is Success)
    }

    @Test
    fun validateSentToDeliveredTransition() {
        val result = validator.validate(
            currentStatus = Sent,
            newStatus = Delivered,
        )
        assertTrue(result is Success)
    }

    @Test
    fun validateDeliveredToReadTransition() {
        val result = validator.validate(
            currentStatus = Delivered,
            newStatus = Read,
        )
        assertTrue(result is Success)
    }

    @Test
    fun validateReadToAnyTransition() {
        // Test transition to Sent
        var result = validator.validate(
            currentStatus = Read,
            newStatus = Sent,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromRead, (result as Failure).error)

        // Test transition to Delivered
        result = validator.validate(
            currentStatus = Read,
            newStatus = Delivered,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromRead, (result as Failure).error)

        // Test transition to Sending
        result = validator.validate(
            currentStatus = Read,
            newStatus = Sending(50),
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromRead, (result as Failure).error)

        // Test transition to Failed
        result = validator.validate(
            currentStatus = Read,
            newStatus = Failed(DeliveryError.NetworkUnavailable),
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromRead, (result as Failure).error)

        // Test transition to null
        result = validator.validate(
            currentStatus = Read,
            newStatus = null,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromRead, (result as Failure).error)
    }

    @Test
    fun validateNullToNonSendingTransition() {
        // Test transition to Sent
        var result = validator.validate(
            currentStatus = null,
            newStatus = Sent,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromUndefinedToOtherThanSending, (result as Failure).error)

        // Test transition to Delivered
        result = validator.validate(
            currentStatus = null,
            newStatus = Delivered,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromUndefinedToOtherThanSending, (result as Failure).error)

        // Test transition to Read
        result = validator.validate(
            currentStatus = null,
            newStatus = Read,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromUndefinedToOtherThanSending, (result as Failure).error)

        // Test transition to Failed
        result = validator.validate(
            currentStatus = null,
            newStatus = Failed(DeliveryError.NetworkUnavailable),
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromUndefinedToOtherThanSending, (result as Failure).error)

        // Test transition to null
        result = validator.validate(
            currentStatus = null,
            newStatus = null,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromUndefinedToOtherThanSending, (result as Failure).error)
    }

    @Test
    fun validateDeliveredToInvalidTransitions() {
        // Test transition to Sending
        var result = validator.validate(
            currentStatus = Delivered,
            newStatus = Sending(50),
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromDeliveredToSending, (result as Failure).error)

        // Test transition to Failed
        result = validator.validate(
            currentStatus = Delivered,
            newStatus = Failed(DeliveryError.NetworkUnavailable),
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromDeliveredToFailed, (result as Failure).error)

        // Test transition to Sent
        result = validator.validate(
            currentStatus = Delivered,
            newStatus = Sent,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromDeliveredToSent, (result as Failure).error)

        // Test transition to null
        result = validator.validate(
            currentStatus = Delivered,
            newStatus = null,
        )
        assertTrue(result is Failure)
        assertEquals(CannotChangeFromDeliveredToUndefined, (result as Failure).error)
    }
}
