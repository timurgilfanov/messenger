package timur.gilfanov.messenger.data.source.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Remote data source for synchronization operations.
 * Handles delta updates for efficient data synchronization with the server.
 */
interface RemoteSyncDataSource {

    /**
     * Returns a flow of chat list delta updates from the server.
     * @param since Timestamp from which to fetch updates. If null, fetches all data.
     * @return Flow that emits delta updates for efficient synchronization.
     */
    fun chatsDeltaUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>>
}
