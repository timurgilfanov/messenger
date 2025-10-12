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
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChatListDeltaDto
import timur.gilfanov.messenger.data.source.remote.dto.toDomain
import timur.gilfanov.messenger.data.source.remote.network.ApiRoutes
import timur.gilfanov.messenger.data.source.remote.network.ErrorMapper
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

/**
 * Implementation of [RemoteSyncDataSource] that provides real-time synchronization of app data
 * using HTTP long polling.
 *
 * ## Current Architecture (Chat-Only Sync):
 * - Provides [chatsDeltaUpdates] for chat synchronization
 * - HTTP long polling with adaptive delays (2s idle, 0.5s when catching up)
 * - Delta-based incremental updates using timestamps
 *
 * ## Planned Architecture (Unified Sync Channel):
 * Will be extended to synchronize multiple entity types (chats + settings) through a single
 * unified stream, reducing network overhead and providing consistent timestamps.
 *
 * ### Migration Plan:
 *
 * 1. **Create Unified SyncDelta Entity**:
 * ```kotlin
 * data class SyncDelta(
 *     val chatChanges: List<ChatChange>,    // Existing
 *     val settingsChange: Settings?,        // NEW - only present when settings changed
 *     val toTimestamp: Instant,             // Existing
 *     val hasMoreChanges: Boolean           // Existing
 * )
 * ```
 *
 * 2. **Add Unified deltaUpdates() Method to Interface**:
 * ```kotlin
 * interface RemoteSyncDataSource {
 *     @Deprecated("Use deltaUpdates() instead")
 *     fun chatsDeltaUpdates(since: Instant?): Flow<...>
 *
 *     fun deltaUpdates(  // NEW
 *         since: Instant?
 *     ): Flow<ResultWithError<SyncDelta, RemoteDataSourceError>>
 * }
 * ```
 *
 * 3. **Implement deltaUpdates() in RemoteSyncDataSourceImpl**:
 * ```kotlin
 * override fun deltaUpdates(
 *     since: Instant?
 * ): Flow<ResultWithError<SyncDelta, RemoteDataSourceError>> = flow {
 *     try {
 *         logger.d(TAG, "Starting unified delta sync from: $since")
 *         var currentSince = since
 *
 *         while (true) {
 *             logger.d(TAG, "Fetching unified delta from: $currentSince")
 *
 *             val response: ApiResponse<SyncDeltaDto> = httpClient.get(
 *                 ApiRoutes.SYNC_DELTAS,  // NEW unified endpoint
 *             ) {
 *                 contentType(ContentType.Application.Json)
 *                 if (currentSince != null) {
 *                     parameter("since", currentSince.toString())
 *                 }
 *             }.body()
 *
 *             if (response.success && response.data != null) {
 *                 val delta = response.data.toDomain()
 *                 logger.d(TAG, "Received unified delta: " +
 *                     "${delta.chatChanges.size} chats, " +
 *                     "${if (delta.settingsChange != null) "1 settings" else "0 settings"}")
 *                 emit(ResultWithError.Success(delta))
 *
 *                 currentSince = delta.toTimestamp
 *
 *                 val delayMs = if (delta.hasMoreChanges) {
 *                     SHORT_POLLING_INTERVAL_MS
 *                 } else {
 *                     POLLING_INTERVAL_MS
 *                 }
 *                 delay(delayMs)
 *             } else {
 *                 logger.w(TAG, "Unified delta sync failed: ${response.error?.message}")
 *                 emit(ResultWithError.Failure(handleApiError(response)))
 *                 return@flow
 *             }
 *         }
 *     } catch (e: CancellationException) {
 *         logger.d(TAG, "Unified delta sync cancelled")
 *         throw e
 *     } catch (e: SerializationException) {
 *         logger.e(TAG, "Failed to serialize/deserialize unified delta data", e)
 *         emit(ResultWithError.Failure(RemoteDataSourceError.ServerError))
 *     } catch (e: SocketTimeoutException) {
 *         logger.e(TAG, "Request timed out while fetching unified deltas", e)
 *         emit(ResultWithError.Failure(RemoteDataSourceError.ServerUnreachable))
 *     } catch (e: IOException) {
 *         logger.e(TAG, "Network error while fetching unified deltas", e)
 *         emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
 *     } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
 *         logger.e(TAG, "Unexpected error while fetching unified deltas", e)
 *         emit(ResultWithError.Failure(ErrorMapper.mapException(e)))
 *     }
 * }
 * ```
 *
 * 4. **Server-Side Changes Required**:
 *    - New API endpoint: `GET /api/sync/deltas?since=<timestamp>`
 *    - Returns both chat changes and settings change in single response
 *    - Settings change only included if settings modified since `since` timestamp
 *    - Response format:
 * ```json
 * {
 *   "success": true,
 *   "data": {
 *     "chatChanges": [...],
 *     "settingsChange": {  // null if settings not changed
 *       "language": "de",
 *       "metadata": {
 *         "isDefault": false,
 *         "lastModifiedAt": "2025-10-12T10:30:00Z",
 *         "lastSyncedAt": "2025-10-12T10:30:00Z"
 *       }
 *     },
 *     "toTimestamp": "2025-10-12T10:30:00Z",
 *     "hasMoreChanges": false
 *   }
 * }
 * ```
 *
 * ### Benefits of Unified Sync:
 * - **Single HTTP Connection**: One polling loop instead of multiple
 * - **Consistent Timestamps**: All entities use same `toTimestamp`
 * - **Atomic Updates**: Changes that happen together arrive together
 * - **Reduced Latency**: No delay between separate entity updates
 * - **Better Resource Usage**: Less battery, data, and CPU usage
 * - **Simpler Testing**: Single source to mock and verify
 * - **Scalable**: Easy to add more entity types (messages, user profile, etc.)
 *
 * ### Migration Path:
 * 1. Add `deltaUpdates()` method alongside existing `chatsDeltaUpdates()`
 * 2. Migrate repositories one by one to use new method
 * 3. Keep both methods during transition period
 * 4. Deprecate `chatsDeltaUpdates()` when all consumers migrated
 * 5. Remove deprecated method after grace period
 *
 * @see MessengerRepositoryImpl for chat sync consumer
 * @see SettingsRepositoryImpl for settings sync consumer (planned)
 */
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
