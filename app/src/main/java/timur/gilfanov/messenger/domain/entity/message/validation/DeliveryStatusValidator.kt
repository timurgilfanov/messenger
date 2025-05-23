package timur.gilfanov.messenger.domain.entity.message.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
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
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToFailed
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToSending
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToUndefined
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromUndefinedToOtherThanSending

class DeliveryStatusValidator {

    private val validTransitions = mapOf(
        Read::class to emptySet(),
        Delivered::class to setOf(Delivered::class, Read::class),
        Sent::class to setOf(Sent::class, Delivered::class, Read::class),
        Failed::class to setOf(
            Failed::class,
            Sending::class,
            Sent::class,
            Delivered::class,
            Read::class,
        ),
        Sending::class to setOf(
            Sending::class,
            Failed::class,
            Sent::class,
            Delivered::class,
            Read::class,
        ),
        null to setOf(Sending::class),
    )

    private val transitionErrors = mapOf(
        Pair(Read::class, null) to CannotChangeFromRead,
        Pair(Delivered::class, null) to CannotChangeFromDeliveredToUndefined,
        Pair(Delivered::class, Sending::class) to CannotChangeFromDeliveredToSending,
        Pair(Delivered::class, Failed::class) to CannotChangeFromDeliveredToFailed,
        Pair(Delivered::class, Sent::class) to CannotChangeFromDeliveredToSent,
        Pair(Sent::class, null) to CannotChangeFromSentToUndefined,
        Pair(Sent::class, Sending::class) to CannotChangeFromSentToSending,
        Pair(Sent::class, Failed::class) to CannotChangeFromSentToFailed,
        Pair(null, null) to CannotChangeFromUndefinedToOtherThanSending,
    )

    fun validate(
        currentStatus: DeliveryStatus?,
        newStatus: DeliveryStatus?,
    ): ResultWithError<Unit, DeliveryStatusValidationError> {
        val currentStatusClass = currentStatus?.let { it::class }
        val newStatusClass = newStatus?.let { it::class }

        val allowedTransitions = validTransitions[currentStatusClass] ?: return Success(Unit)

        if (newStatusClass in allowedTransitions) {
            return Success(Unit)
        }

        val error = transitionErrors[Pair(currentStatusClass, newStatusClass)]
            ?: transitionErrors[Pair(currentStatusClass, null)]
            ?: CannotChangeFromUndefinedToOtherThanSending

        return Failure(error)
    }
}
