package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatDto
import timur.gilfanov.messenger.data.source.remote.dto.JoinChatRequestDto
import timur.gilfanov.messenger.data.source.remote.dto.toCreateRequest
import timur.gilfanov.messenger.data.source.remote.dto.toDomain
import timur.gilfanov.messenger.data.source.remote.network.ApiRoutes
import timur.gilfanov.messenger.data.source.remote.network.ErrorMapper
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.util.Logger

@Singleton
class RemoteChatDataSourceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteChatDataSource {

    companion object {
        private const val TAG = "RemoteChatDataSource"
    }

    private fun handleApiError(response: ApiResponse<*>): RemoteDataSourceError {
        val error = response.error?.let { ErrorMapper.mapErrorResponse(it.code) }
            ?: RemoteDataSourceError.UnknownError(RuntimeException("Unknown API error"))
        logger.w(TAG, "API error: ${response.error?.message ?: "Unknown"}")
        return error
    }

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RemoteDataSourceError> =
        try {
            logger.d(TAG, "Creating chat: ${chat.name}")
            val request = chat.toCreateRequest()

            val response: ApiResponse<ChatDto> = httpClient.post(ApiRoutes.CREATE_CHAT) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.success && response.data != null) {
                logger.d(TAG, "Chat created successfully: ${response.data.id}")
                ResultWithError.Success(response.data.toDomain())
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to serialize/deserialize chat data", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerError)
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while creating chat", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable)
        } catch (e: IOException) {
            logger.e(TAG, "Network error while creating chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        } catch (e: CancellationException) {
            logger.d(TAG, "Chat creation cancelled")
            throw e // Re-throw cancellation to maintain proper cancellation semantics
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Intentional: network resilience
            logger.e(TAG, "Unexpected error while creating chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> =
        try {
            logger.d(TAG, "Deleting chat: ${chatId.id}")
            val response: ApiResponse<Unit> = httpClient.delete(
                ApiRoutes.deleteChatUrl(chatId.id.toString()),
            ).body()

            if (response.success) {
                logger.d(TAG, "Chat deleted successfully: ${chatId.id}")
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to parse delete chat response", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerError)
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while deleting chat", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable)
        } catch (e: IOException) {
            logger.e(TAG, "Network error while deleting chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        } catch (e: CancellationException) {
            logger.d(TAG, "Chat deletion cancelled")
            throw e // Re-throw cancellation to maintain proper cancellation semantics
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Intentional: network resilience
            logger.e(TAG, "Unexpected error while deleting chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RemoteDataSourceError> = try {
        logger.d(TAG, "Joining chat: ${chatId.id}")
        val request = JoinChatRequestDto(inviteLink)

        val response: ApiResponse<ChatDto> = httpClient.post(
            ApiRoutes.joinChatUrl(chatId.id.toString()),
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        if (response.success && response.data != null) {
            logger.d(TAG, "Joined chat successfully: ${response.data.id}")
            ResultWithError.Success(response.data.toDomain())
        } else {
            ResultWithError.Failure(handleApiError(response))
        }
    } catch (e: SerializationException) {
        logger.e(TAG, "Failed to serialize/deserialize join chat data", e)
        ResultWithError.Failure(RemoteDataSourceError.ServerError)
    } catch (e: SocketTimeoutException) {
        logger.e(TAG, "Request timed out while joining chat", e)
        ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable)
    } catch (e: IOException) {
        logger.e(TAG, "Network error while joining chat", e)
        ResultWithError.Failure(ErrorMapper.mapException(e))
    } catch (e: CancellationException) {
        logger.d(TAG, "Chat joining cancelled")
        throw e // Re-throw cancellation to maintain proper cancellation semantics
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Intentional: network resilience
        logger.e(TAG, "Unexpected error while joining chat", e)
        ResultWithError.Failure(ErrorMapper.mapException(e))
    }

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError> =
        try {
            logger.d(TAG, "Leaving chat: ${chatId.id}")
            val response: ApiResponse<Unit> = httpClient.post(
                ApiRoutes.leaveChatUrl(chatId.id.toString()),
            ).body()

            if (response.success) {
                logger.d(TAG, "Left chat successfully: ${chatId.id}")
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to parse leave chat response", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerError)
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while leaving chat", e)
            ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable)
        } catch (e: IOException) {
            logger.e(TAG, "Network error while leaving chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        } catch (e: CancellationException) {
            logger.d(TAG, "Chat leaving cancelled")
            throw e // Re-throw cancellation to maintain proper cancellation semantics
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Intentional: network resilience
            logger.e(TAG, "Unexpected error while leaving chat", e)
            ResultWithError.Failure(ErrorMapper.mapException(e))
        }
}
