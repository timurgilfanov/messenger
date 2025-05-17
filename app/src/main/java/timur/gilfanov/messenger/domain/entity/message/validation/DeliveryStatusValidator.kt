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
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromDeliveredToUndefined
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromSentToUndefined
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError.CannotChangeFromUndefinedToOtherThanSending

class DeliveryStatusValidator {

    fun validate(
        currentStatus: DeliveryStatus?,
        newStatus: DeliveryStatus?,
    ): ResultWithError<Unit, DeliveryStatusValidationError> = when (currentStatus) {
        is Read -> Failure(DeliveryStatusValidationError.CannotChangeFromRead)
        is Delivered -> when (newStatus) {
            null -> Failure(CannotChangeFromDeliveredToUndefined)
            is Sending -> Failure(DeliveryStatusValidationError.CannotChangeFromDeliveredToSending)
            is Failed -> Failure(DeliveryStatusValidationError.CannotChangeFromDeliveredToFailed)
            is Sent -> Failure(DeliveryStatusValidationError.CannotChangeFromDeliveredToSent)
            Delivered, Read -> Success(Unit)
        }
        is Sent -> when (newStatus) {
            null -> Failure(CannotChangeFromSentToUndefined)
            is Sending -> Failure(DeliveryStatusValidationError.CannotChangeFromSentToSending)
            is Failed -> Failure(DeliveryStatusValidationError.CannotChangeFromSentToFailed)
            Sent, Delivered, Read -> Success(Unit)
        }
        is Failed, is Sending -> Success(Unit)
        null -> when (newStatus) {
            is Sending -> Success(Unit)
            else -> Failure(CannotChangeFromUndefinedToOtherThanSending)
        }
    }
}
