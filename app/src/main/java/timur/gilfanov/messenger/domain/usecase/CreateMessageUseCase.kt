package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.Unauthorized

class CreateMessageUseCase(
    private val message: Message,
    private val repository: Repository,
    private val deliveryStatusValidator: DeliveryStatusValidator,
) {

    operator fun invoke() = flow<ResultWithError<Message, CreateMessageError>> {
        val permission = message.recipient.canSendMessage()
        if (permission is Failure) {
            emit(Failure(Unauthorized(permission.error)))
            return@flow
        }

        val validation = message.validate()
        if (validation is Failure) {
            emit(Failure(MessageIsNotValid(validation.error)))
            return@flow
        }

        message.deliveryStatus?.let { status ->
            emit(Failure(DeliveryStatusAlreadySet(status)))
            return@flow
        }

        var prev = message.deliveryStatus
        repository.sendMessage(message).collect { progress ->
            deliveryStatusValidator.validate(prev, progress.deliveryStatus)
                .onSuccess { emit(Success(progress)) }
                .onFailure { error -> emit(Failure(DeliveryStatusUpdateNotValid(error))) }
            prev = progress.deliveryStatus
        }
    }
}
