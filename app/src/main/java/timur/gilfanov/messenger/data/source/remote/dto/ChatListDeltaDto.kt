package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatListDeltaDto(
    val changes: List<ChatDeltaDto>,
    val fromTimestamp: String? = null, // ISO 8601 timestamp, null for full sync
    val toTimestamp: String, // ISO 8601 timestamp
    val hasMoreChanges: Boolean = false, // true if pagination needed
)
