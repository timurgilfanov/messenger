package timur.gilfanov.messenger.entity.validation

import timur.gilfanov.messenger.entity.model.message.DeliveryStatus
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus.Failed
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus.Read
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus.Sent

class DeliveryStatusValidator {

    fun validate(currentStatus: DeliveryStatus, newStatus: DeliveryStatus): Result<Unit> =
        when (currentStatus) {
            is Read -> {
                Result.failure(
                    IllegalStateException("Cannot change delivery status once it's Read"),
                )
            }
            is Delivered -> {
                when (newStatus) {
                    is Sending -> {
                        Result.failure(
                            IllegalStateException("Cannot change status from Delivered to Sending"),
                        )
                    }
                    is Failed -> {
                        Result.failure(
                            IllegalStateException("Cannot change status from Delivered to Failed"),
                        )
                    }
                    is Sent -> {
                        Result.failure(
                            IllegalStateException("Cannot change status from Delivered to Sent"),
                        )
                    }
                    Delivered, Read -> Result.success(Unit)
                }
            }
            is Sent -> {
                when (newStatus) {
                    is Sending -> {
                        Result.failure(
                            IllegalStateException("Cannot change status from Sent to Sending"),
                        )
                    }
                    is Failed -> {
                        Result.failure(
                            IllegalStateException("Cannot change status from Sent to Failed"),
                        )
                    }
                    Sent, Delivered, Read -> Result.success(Unit)
                }
            }
            is Failed, is Sending -> Result.success(Unit)
        }
}
