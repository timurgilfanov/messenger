package timur.gilfanov.messenger.data.source.remote.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode

/**
 * Maps network exceptions to RemoteDataSourceError.
 */
object ErrorMapper {

    fun mapException(exception: Throwable): RemoteDataSourceError = when (exception) {
        is UnknownHostException -> RemoteDataSourceError.NetworkNotAvailable
        is ConnectException -> RemoteDataSourceError.ServerUnreachable
        is TimeoutException -> RemoteDataSourceError.ServerUnreachable

        is ClientRequestException -> mapClientError(exception.response.status)
        is ServerResponseException -> mapServerError(exception.response.status)

        else -> RemoteDataSourceError.UnknownError(exception)
    }

    private fun mapClientError(status: HttpStatusCode): RemoteDataSourceError = when (status) {
        HttpStatusCode.Unauthorized -> RemoteDataSourceError.Unauthorized
        HttpStatusCode.NotFound -> RemoteDataSourceError.ChatNotFound
        HttpStatusCode.Forbidden -> RemoteDataSourceError.UserBlocked
        HttpStatusCode.Conflict -> RemoteDataSourceError.AlreadyJoined
        HttpStatusCode.Gone -> RemoteDataSourceError.ExpiredInviteLink
        HttpStatusCode.UnprocessableEntity -> RemoteDataSourceError.InvalidInviteLink
        HttpStatusCode.TooManyRequests -> RemoteDataSourceError.RateLimitExceeded
        HttpStatusCode.BadRequest -> RemoteDataSourceError.ChatClosed
        else -> RemoteDataSourceError.UnknownError(
            RuntimeException("Client error: ${status.value} ${status.description}"),
        )
    }

    private fun mapServerError(status: HttpStatusCode): RemoteDataSourceError = when (status) {
        HttpStatusCode.InternalServerError -> RemoteDataSourceError.ServerError
        HttpStatusCode.BadGateway -> RemoteDataSourceError.ServerUnreachable
        HttpStatusCode.ServiceUnavailable -> RemoteDataSourceError.ServerError
        HttpStatusCode.GatewayTimeout -> RemoteDataSourceError.ServerUnreachable
        else -> RemoteDataSourceError.ServerError
    }

    @Suppress("CyclomaticComplexMethod") // Comprehensive error code mapping
    fun mapErrorResponse(apiErrorCode: ApiErrorCode): RemoteDataSourceError = when (apiErrorCode) {
        is ApiErrorCode.ChatNotFound -> RemoteDataSourceError.ChatNotFound
        is ApiErrorCode.MessageNotFound -> RemoteDataSourceError.MessageNotFound
        is ApiErrorCode.InvalidInviteLink -> RemoteDataSourceError.InvalidInviteLink
        is ApiErrorCode.ExpiredInviteLink -> RemoteDataSourceError.ExpiredInviteLink
        is ApiErrorCode.ChatClosed -> RemoteDataSourceError.ChatClosed
        is ApiErrorCode.AlreadyJoined -> RemoteDataSourceError.AlreadyJoined
        is ApiErrorCode.ChatFull -> RemoteDataSourceError.ChatFull
        is ApiErrorCode.UserBlocked -> RemoteDataSourceError.UserBlocked
        is ApiErrorCode.RateLimitExceeded -> RemoteDataSourceError.RateLimitExceeded
        is ApiErrorCode.CooldownActive -> RemoteDataSourceError.CooldownActive(
            apiErrorCode.remaining,
        )
        is ApiErrorCode.Unauthorized -> RemoteDataSourceError.Unauthorized
        is ApiErrorCode.NetworkNotAvailable -> RemoteDataSourceError.NetworkNotAvailable
        is ApiErrorCode.ServerUnreachable -> RemoteDataSourceError.ServerUnreachable
        is ApiErrorCode.ServerError -> RemoteDataSourceError.ServerError
        is ApiErrorCode.Unknown -> RemoteDataSourceError.UnknownError(
            RuntimeException("Unknown error: ${apiErrorCode.originalCode}"),
        )
    }
}
