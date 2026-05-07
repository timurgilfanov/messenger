package timur.gilfanov.messenger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError

internal class SendMessageFlowFactory(
    private val localDataSources: LocalDataSources,
    private val remoteDataSources: RemoteDataSources,
) {

    suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, SendMessageRepositoryError>> {
        val acceptedMessage = message.withDeliveryStatus(DeliveryStatus.Sending(0))
        return when (val insertResult = localDataSources.message.insertMessage(acceptedMessage)) {
            is ResultWithError.Failure -> flow {
                emit(insertResult.error.toRepositoryFailure())
            }

            is ResultWithError.Success -> remoteSendFlow(acceptedMessage)
        }
    }

    private fun remoteSendFlow(
        acceptedMessage: Message,
    ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flow {
        emit(ResultWithError.Success(acceptedMessage))
        var lastEmittedMessage = acceptedMessage

        remoteDataSources.message.sendMessage(acceptedMessage).collect { result ->
            when (result) {
                is ResultWithError.Success -> {
                    val updatedMessage = result.data
                    if (updatedMessage != lastEmittedMessage) {
                        val updateResult = updateLocalMessage(updatedMessage)
                        emit(updateResult)
                        if (updateResult is ResultWithError.Success) {
                            lastEmittedMessage = updatedMessage
                        }
                    }
                }

                is ResultWithError.Failure -> {
                    val failedMessage = acceptedMessage.withDeliveryStatus(
                        DeliveryStatus.Failed(result.error.toDeliveryError()),
                    )
                    emit(updateFailedMessage(failedMessage, result.error))
                }
            }
        }
    }

    private suspend fun updateLocalMessage(
        message: Message,
    ): ResultWithError<Message, SendMessageRepositoryError> =
        when (val updateResult = localDataSources.message.updateMessage(message)) {
            is ResultWithError.Success -> ResultWithError.Success(message)
            is ResultWithError.Failure -> updateResult.error.toRepositoryFailure()
        }

    private suspend fun updateFailedMessage(
        failedMessage: Message,
        remoteError: RemoteDataSourceError,
    ): ResultWithError<Message, SendMessageRepositoryError> =
        when (val updateResult = localDataSources.message.updateMessage(failedMessage)) {
            is ResultWithError.Success -> ResultWithError.Failure(
                mapRemoteErrorToSendMessageError(remoteError),
            )

            is ResultWithError.Failure -> updateResult.error.toRepositoryFailure()
        }

    private fun LocalDataSourceError.toRepositoryFailure() =
        ResultWithError.Failure<Message, SendMessageRepositoryError>(
            SendMessageRepositoryError.LocalOperationFailed(toLocalStorageError()),
        )
}

private fun Message.withDeliveryStatus(status: DeliveryStatus): Message = when (this) {
    is TextMessage -> copy(deliveryStatus = status)
    else -> this
}

private fun LocalDataSourceError.toLocalStorageError(): LocalStorageError = when (this) {
    LocalDataSourceError.StorageUnavailable,
    LocalDataSourceError.ConcurrentModificationError,
    -> LocalStorageError.TemporarilyUnavailable

    LocalDataSourceError.StorageFull -> LocalStorageError.StorageFull
    is LocalDataSourceError.UnknownError -> LocalStorageError.UnknownError(cause)
    LocalDataSourceError.ChatNotFound,
    LocalDataSourceError.MessageNotFound,
    LocalDataSourceError.ParticipantNotFound,
    is LocalDataSourceError.DuplicateEntity,
    is LocalDataSourceError.RelatedEntityMissing,
    is LocalDataSourceError.InvalidData,
    -> LocalStorageError.UnknownError(
        IllegalStateException("Unexpected local message storage error: $this"),
    )
}

private fun RemoteDataSourceError.toDeliveryError(): DeliveryError = when (this) {
    RemoteDataSourceError.NetworkNotAvailable -> DeliveryError.NetworkUnavailable
    RemoteDataSourceError.ServerUnreachable,
    RemoteDataSourceError.ServerError,
    RemoteDataSourceError.RateLimitExceeded,
    is RemoteDataSourceError.CooldownActive,
    -> DeliveryError.ServerUnreachable

    RemoteDataSourceError.Unauthorized -> DeliveryError.UnknownError(
        errorCode = DELIVERY_ERROR_UNAUTHORIZED,
        message = "Unauthorized",
    )

    is RemoteDataSourceError.UnknownError -> DeliveryError.UnknownError(
        errorCode = DELIVERY_ERROR_UNKNOWN,
        message = cause.message,
    )

    RemoteDataSourceError.ChatNotFound,
    RemoteDataSourceError.MessageNotFound,
    RemoteDataSourceError.InvalidInviteLink,
    RemoteDataSourceError.ExpiredInviteLink,
    RemoteDataSourceError.ChatClosed,
    RemoteDataSourceError.AlreadyJoined,
    RemoteDataSourceError.ChatFull,
    RemoteDataSourceError.UserBlocked,
    -> DeliveryError.UnknownError(
        errorCode = DELIVERY_ERROR_UNEXPECTED,
        message = toString(),
    )
}

private const val DELIVERY_ERROR_UNAUTHORIZED = 401
private const val DELIVERY_ERROR_UNKNOWN = -1
private const val DELIVERY_ERROR_UNEXPECTED = -2
