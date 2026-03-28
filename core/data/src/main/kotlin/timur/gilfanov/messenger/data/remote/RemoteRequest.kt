package timur.gilfanov.messenger.data.remote

import io.ktor.client.network.sockets.SocketTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.util.Logger

suspend inline fun <T, E> executeRemoteRequest(
    tag: String,
    operationName: String,
    logger: Logger,
    crossinline wrapError: (RemoteDataSourceError) -> E,
    crossinline block: suspend () -> ResultWithError<T, E>,
): ResultWithError<T, E> = try {
    logger.d(tag, "Executing $operationName")
    block()
} catch (e: SerializationException) {
    logger.e(tag, "Serialization error in $operationName", e)
    ResultWithError.Failure(wrapError(RemoteDataSourceError.ServerError))
} catch (e: SocketTimeoutException) {
    logger.e(tag, "Timeout in $operationName", e)
    ResultWithError.Failure(wrapError(RemoteDataSourceError.ServiceUnavailable.Timeout))
} catch (e: UnknownHostException) {
    logger.e(tag, "Network unavailable in $operationName", e)
    ResultWithError.Failure(wrapError(RemoteDataSourceError.ServiceUnavailable.NetworkNotAvailable))
} catch (e: ConnectException) {
    logger.e(tag, "Server unreachable in $operationName", e)
    ResultWithError.Failure(wrapError(RemoteDataSourceError.ServiceUnavailable.ServerUnreachable))
} catch (e: IOException) {
    logger.e(tag, "IO error in $operationName", e)
    ResultWithError.Failure(wrapError(RemoteDataSourceError.ServiceUnavailable.ServerUnreachable))
} catch (e: CancellationException) {
    logger.d(tag, "$operationName cancelled")
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    logger.e(tag, "Unexpected error in $operationName", e)
    ResultWithError.Failure(
        wrapError(RemoteDataSourceError.UnknownServiceError(ErrorReason(e.message ?: "Unknown"))),
    )
}
