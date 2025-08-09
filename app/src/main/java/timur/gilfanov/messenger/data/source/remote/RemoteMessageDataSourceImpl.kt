package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.MessageDto
import timur.gilfanov.messenger.data.source.remote.dto.toDomain
import timur.gilfanov.messenger.data.source.remote.dto.toEditRequest
import timur.gilfanov.messenger.data.source.remote.dto.toRequestDto
import timur.gilfanov.messenger.data.source.remote.dto.toSendRequest
import timur.gilfanov.messenger.data.source.remote.network.ApiRoutes
import timur.gilfanov.messenger.data.source.remote.network.ErrorMapper
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.util.Logger

@Singleton
class RemoteMessageDataSourceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteMessageDataSource {

    companion object {
        private const val TAG = "RemoteMessageDataSource"
        private const val SENDING_SIMULATION_DELAY_MS = 100L
        private const val SENDING_PROGRESS_STEPS = 10
    }

    private fun handleApiError(response: ApiResponse<*>): RemoteDataSourceError {
        val error = response.error?.let { ErrorMapper.mapErrorResponse(it.code) }
            ?: RemoteDataSourceError.UnknownError(RuntimeException("Unknown API error"))
        logger.w(TAG, "API error: ${response.error?.message ?: "Unknown"}")
        return error
    }

    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RemoteDataSourceError>> = flow {
        try {
            logger.d(TAG, "Sending message: ${message.id}")
            val request = message.toSendRequest()

            // Emit sending progress updates
            for (progress in 1..SENDING_PROGRESS_STEPS) {
                @Suppress("MagicNumber") // Standard percentage calculation
                val progressPercent = (progress * 100) / SENDING_PROGRESS_STEPS
                val updatedMessage = when (message) {
                    is timur.gilfanov.messenger.domain.entity.message.TextMessage -> message.copy(
                        deliveryStatus = DeliveryStatus.Sending(progressPercent),
                    )
                    else -> message
                }
                emit(ResultWithError.Success(updatedMessage))
                delay(SENDING_SIMULATION_DELAY_MS)
            }

            // Send actual request to server
            val response: ApiResponse<MessageDto> = httpClient.post(ApiRoutes.SEND_MESSAGE) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.success && response.data != null) {
                logger.d(TAG, "Message sent successfully: ${response.data.id}")
                emit(ResultWithError.Success(response.data.toDomain()))
            } else {
                emit(ResultWithError.Failure(handleApiError(response)))
            }
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to serialize/deserialize message data", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerError))
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while sending message", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable))
        } catch (e: IOException) {
            logger.e(TAG, "Network error while sending message", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Intentional: network resilience
            logger.e(TAG, "Unexpected error while sending message", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        }
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RemoteDataSourceError>> = flow {
        try {
            logger.d(TAG, "Editing message: ${message.id}")
            val request = message.toEditRequest()

            val response: ApiResponse<MessageDto> = httpClient.put(
                ApiRoutes.editMessageUrl(message.id.id.toString()),
            ) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.success && response.data != null) {
                logger.d(TAG, "Message edited successfully: ${response.data.id}")
                emit(ResultWithError.Success(response.data.toDomain()))
            } else {
                emit(ResultWithError.Failure(handleApiError(response)))
            }
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to serialize/deserialize edit message data", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerError))
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while editing message", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable))
        } catch (e: IOException) {
            logger.e(TAG, "Network error while editing message", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Intentional: network resilience
            logger.e(TAG, "Unexpected error while editing message", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        }
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RemoteDataSourceError> = try {
        logger.d(TAG, "Deleting message: ${messageId.id} with mode: $mode")
        val request = mode.toRequestDto()

        val response: ApiResponse<Unit> = httpClient.delete(
            ApiRoutes.deleteMessageUrl(messageId.id.toString()),
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        if (response.success) {
            logger.d(TAG, "Message deleted successfully: ${messageId.id}")
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(handleApiError(response))
        }
    } catch (e: SerializationException) {
        logger.e(TAG, "Failed to serialize/deserialize delete message data", e)
        ResultWithError.Failure(RemoteDataSourceError.ServerError)
    } catch (e: SocketTimeoutException) {
        logger.e(TAG, "Request timed out while deleting message", e)
        ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable)
    } catch (e: IOException) {
        logger.e(TAG, "Network error while deleting message", e)
        ResultWithError.Failure(ErrorMapper.mapException(e))
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Intentional: network resilience
        logger.e(TAG, "Unexpected error while deleting message", e)
        ResultWithError.Failure(ErrorMapper.mapException(e))
    }
}
