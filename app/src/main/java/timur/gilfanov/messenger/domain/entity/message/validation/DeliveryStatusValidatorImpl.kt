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
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeAnyStatusToUndefined
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToFailed
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToSending
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToSent
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromRead
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToFailed
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToSending
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromUndefinedToOtherThanSending

class DeliveryStatusValidatorImpl : DeliveryStatusValidator {

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
        Pair(Read::class, Sending::class) to CannotChangeFromRead,
        Pair(Read::class, Failed::class) to CannotChangeFromRead,
        Pair(Read::class, Sent::class) to CannotChangeFromRead,
        Pair(Read::class, Delivered::class) to CannotChangeFromRead,
        Pair(Delivered::class, null) to CannotChangeAnyStatusToUndefined,
        Pair(Delivered::class, Sending::class) to CannotChangeFromDeliveredToSending,
        Pair(Delivered::class, Failed::class) to CannotChangeFromDeliveredToFailed,
        Pair(Delivered::class, Sent::class) to CannotChangeFromDeliveredToSent,
        Pair(Sent::class, null) to CannotChangeAnyStatusToUndefined,
        Pair(Sent::class, Sending::class) to CannotChangeFromSentToSending,
        Pair(Sent::class, Failed::class) to CannotChangeFromSentToFailed,
        Pair(Failed::class, null) to CannotChangeAnyStatusToUndefined,
        Pair(Sending::class, null) to CannotChangeAnyStatusToUndefined,
        Pair(null, null) to CannotChangeFromUndefinedToOtherThanSending,
        Pair(null, Failed::class) to CannotChangeFromUndefinedToOtherThanSending,
        Pair(null, Sent::class) to CannotChangeFromUndefinedToOtherThanSending,
        Pair(null, Delivered::class) to CannotChangeFromUndefinedToOtherThanSending,
        Pair(null, Read::class) to CannotChangeFromUndefinedToOtherThanSending,

    )

    override fun validate(
        currentStatus: DeliveryStatus?,
        newStatus: DeliveryStatus?,
    ): ResultWithError<Unit, DeliveryStatusValidationError> {
        val currentStatusClass = currentStatus?.let { it::class }
        val newStatusClass = newStatus?.let { it::class }

        validTransitions[currentStatusClass]?.let { allowedTransitions ->
            if (newStatusClass in allowedTransitions) {
                return Success(Unit)
            }
        }

        val error = transitionErrors[Pair(currentStatusClass, newStatusClass)]!!
        return Failure(error)
    }
}
