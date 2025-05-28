package timur.gilfanov.messenger.domain.entity.message.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus

interface DeliveryStatusValidator {
    fun validate(
        currentStatus: DeliveryStatus?,
        newStatus: DeliveryStatus?,
    ): ResultWithError<Unit, DeliveryStatusValidationError>
}
