package timur.gilfanov.messenger.domain.usecase.message

import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.repository.EditMessageRepositoryError

/**
 * Errors for message editing operations.
 *
 * ## Logical Errors
 * - [EditWindowExpired] - Edit window has expired
 * - [MessageIsNotValid] - Message validation failed
 * - [DeliveryStatusAlreadySet] - Delivery status already set
 * - [DeliveryStatusUpdateNotValid] - Delivery status update validation failed
 * - [CreationTimeChanged] - Creation time was changed
 * - [RecipientChanged] - Recipient was changed
 * - [SenderIdChanged] - Sender ID was changed
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface EditMessageError {
    /**
     * Edit window has expired.
     */
    data object EditWindowExpired : EditMessageError

    /**
     * Message validation failed.
     *
     * @property reason The validation error details
     */
    data class MessageIsNotValid(val reason: ValidationError) : EditMessageError

    /**
     * Delivery status is already set to this value.
     *
     * @property status The current delivery status
     */
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : EditMessageError

    /**
     * Delivery status update validation failed.
     *
     * @property error The validation error details
     */
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        EditMessageError

    /**
     * Creation time was changed, which is not allowed.
     */
    data object CreationTimeChanged : EditMessageError

    /**
     * Recipient was changed, which is not allowed.
     */
    data object RecipientChanged : EditMessageError

    /**
     * Sender ID was changed, which is not allowed.
     */
    data object SenderIdChanged : EditMessageError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : EditMessageError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : EditMessageError
}

internal fun EditMessageRepositoryError.toUseCaseError(): EditMessageError = when (this) {
    is EditMessageRepositoryError.LocalOperationFailed ->
        EditMessageError.LocalOperationFailed(error)
    is EditMessageRepositoryError.RemoteOperationFailed ->
        EditMessageError.RemoteOperationFailed(error)
}
