package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

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
