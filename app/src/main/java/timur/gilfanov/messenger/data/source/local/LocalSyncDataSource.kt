package timur.gilfanov.messenger.data.source.local

import kotlin.time.Instant
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Local data source for synchronization-related operations.
 * Handles sync timestamps and delta application for data synchronization.
 */
interface LocalSyncDataSource {

    /**
     * Retrieves the last synchronization timestamp.
     * Returns null if no synchronization has occurred yet.
     */
    suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError>

    /**
     * Updates the last synchronization timestamp.
     */
    suspend fun updateLastSyncTimestamp(
        timestamp: Instant,
    ): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Applies a single chat delta to local storage.
     * Used for incremental updates of individual chats.
     */
    suspend fun applyChatDelta(delta: ChatDelta): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Applies a chat list delta to local storage.
     * Used for batch updates from server synchronization.
     */
    suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError>
}
