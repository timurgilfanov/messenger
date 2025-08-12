package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Represents a collection of incremental changes to the chat list.
 * Contains all changes that occurred since a specific timestamp.
 */
data class ChatListDelta(
    val changes: ImmutableList<ChatDelta>,
    val fromTimestamp: Instant?, // null indicates full sync (first time)
    val toTimestamp: Instant,
    val hasMoreChanges: Boolean = false, // true if there are more changes to fetch
) {
    companion object {
        fun fullSync(changes: List<ChatDelta>, timestamp: Instant) = ChatListDelta(
            changes = changes.toImmutableList(),
            fromTimestamp = null, // null indicates this is a full sync
            toTimestamp = timestamp,
            hasMoreChanges = false,
        )
    }
}
