package timur.gilfanov.messenger.domain.entity.message.validation

import timur.gilfanov.messenger.domain.entity.ValidationError

sealed class DeliveryStatusValidationError : ValidationError {
    object CannotChangeFromRead : DeliveryStatusValidationError()
    object CannotChangeFromDeliveredToUndefined : DeliveryStatusValidationError()
    object CannotChangeFromDeliveredToSending : DeliveryStatusValidationError()
    object CannotChangeFromDeliveredToFailed : DeliveryStatusValidationError()
    object CannotChangeFromDeliveredToSent : DeliveryStatusValidationError()
    object CannotChangeFromSentToSending : DeliveryStatusValidationError()
    object CannotChangeFromSentToFailed : DeliveryStatusValidationError()
    object CannotChangeFromSentToUndefined : DeliveryStatusValidationError()
    object CannotChangeFromUndefinedToOtherThanSending : DeliveryStatusValidationError()
    object CannotChangeAnyStatusToUndefined : DeliveryStatusValidationError()
}
