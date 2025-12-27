package timur.gilfanov.messenger.domain.usecase.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for message sending operations.
 *
 * ## Logical Errors
 * - [WaitAfterJoining] - Must wait after joining before sending
 * - [WaitDebounce] - Must wait between messages
 * - [MessageIsNotValid] - Message validation failed
 * - [DeliveryStatusAlreadySet] - Delivery status already set
 * - [DeliveryStatusUpdateNotValid] - Delivery status update validation failed
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SendMessageError {
    /**
     * Must wait after joining before sending messages.
     *
     * @property duration Time to wait before sending
     */
    data class WaitAfterJoining(val duration: Duration) : SendMessageError

    /**
     * Must wait between messages.
     *
     * @property duration Time to wait before next message
     */
    data class WaitDebounce(val duration: Duration) : SendMessageError

    /**
     * Message validation failed.
     *
     * @property reason The validation error details
     */
    data class MessageIsNotValid(val reason: ValidationError) : SendMessageError

    /**
     * Delivery status is already set to this value.
     *
     * @property status The current delivery status
     */
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : SendMessageError

    /**
     * Delivery status update validation failed.
     *
     * @property error The validation error details
     */
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        SendMessageError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : SendMessageError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : SendMessageError
}
