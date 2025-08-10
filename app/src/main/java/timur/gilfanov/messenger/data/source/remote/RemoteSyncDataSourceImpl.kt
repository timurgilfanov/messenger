package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatListDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.toDomain
import timur.gilfanov.messenger.data.source.remote.network.ApiRoutes
import timur.gilfanov.messenger.data.source.remote.network.ErrorMapper
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

@Singleton
class RemoteSyncDataSourceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteSyncDataSource {

    companion object {
        private const val TAG = "RemoteSyncDataSource"
        private const val POLLING_INTERVAL_MS = 2000L
        private const val SHORT_POLLING_INTERVAL_MS = POLLING_INTERVAL_MS / 4
    }

    private fun handleApiError(response: ApiResponse<*>): RemoteDataSourceError {
        val error = response.error?.let { ErrorMapper.mapErrorResponse(it.code) }
            ?: RemoteDataSourceError.UnknownError(RuntimeException("Unknown API error"))
        logger.w(TAG, "API error: ${response.error?.message ?: "Unknown"}")
        return error
    }

    override fun chatsDeltaUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>> = flow {
        try {
            logger.d(TAG, "Starting delta sync from: $since")

            // Simulate continuous HTTP polling for delta updates
            var currentSince = since

            // Continuous sync with adaptive delays
            while (true) {
                logger.d(TAG, "Fetching delta from: $currentSince")

                val response: ApiResponse<ChatListDeltaDto> = httpClient.get(
                    ApiRoutes.CHAT_DELTAS,
                ) {
                    contentType(ContentType.Application.Json)
                    if (currentSince != null) {
                        parameter("since", currentSince.toString())
                    }
                }.body()

                if (response.success && response.data != null) {
                    val delta = response.data.toDomain()
                    logger.d(TAG, "Received delta with ${delta.changes.size} changes")
                    emit(ResultWithError.Success(delta))

                    // Update timestamp for next iteration
                    currentSince = delta.toTimestamp

                    // Adaptive delay based on hasMoreChanges
                    val delayMs = if (delta.hasMoreChanges) {
                        SHORT_POLLING_INTERVAL_MS
                    } else {
                        POLLING_INTERVAL_MS
                    }
                    delay(delayMs)
                } else {
                    logger.w(TAG, "Delta sync failed: ${response.error?.message}")
                    emit(ResultWithError.Failure(handleApiError(response)))
                    return@flow
                }
            }
        } catch (e: CancellationException) {
            logger.d(TAG, "Delta sync cancelled")
            throw e
        } catch (e: SerializationException) {
            logger.e(TAG, "Failed to serialize/deserialize delta data", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerError))
        } catch (e: SocketTimeoutException) {
            logger.e(TAG, "Request timed out while fetching deltas", e)
            emit(ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable))
        } catch (e: IOException) {
            logger.e(TAG, "Network error while fetching deltas", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(TAG, "Unexpected error while fetching deltas", e)
            emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
        }
    }
}
