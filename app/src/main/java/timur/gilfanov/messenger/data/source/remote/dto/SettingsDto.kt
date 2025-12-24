package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Network DTOs for settings-related operations.
 */

/**
 * Response DTO for GET /settings endpoint.
 * Contains a list of all user settings from the server.
 */
@Serializable
data class SettingsResponseDto(val settings: List<SettingItemDto>)

/**
 * Single setting item in server responses.
 * Used for both GET settings response and sync responses.
 */
@Serializable
data class SettingItemDto(val key: String, val value: String, val version: Int)

/**
 * Request DTO for PUT /settings/change endpoint.
 * Contains a list of settings to change.
 */
@Serializable
data class ChangeSettingsRequestDto(val settings: List<SettingChangeDto>)

/**
 * Single setting change in request body.
 */
@Serializable
data class SettingChangeDto(val key: String, val value: String)

/**
 * Request DTO for POST /settings/sync endpoint.
 * Contains a list of settings to sync with Last Write Wins conflict resolution.
 */
@Serializable
data class SyncSettingsRequestDto(val settings: List<SettingSyncItemDto>)

/**
 * Single setting sync request item.
 */
@Serializable
data class SettingSyncItemDto(
    val key: String,
    val value: String,
    val clientVersion: Int,
    val lastKnownServerVersion: Int,
    val modifiedAt: String,
)

/**
 * Response DTO for POST /settings/sync endpoint.
 * Contains sync results for each setting in the request.
 */
@Serializable
data class SyncSettingsResponseDto(val results: List<SettingSyncResultDto>)

/**
 * Single setting sync result.
 * Can be either success (client value accepted) or conflict (server value wins).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed class SettingSyncResultDto {
    abstract val key: String
    abstract val newVersion: Int

    @Serializable
    @SerialName("success")
    data class Success(override val key: String, override val newVersion: Int) :
        SettingSyncResultDto()

    @Serializable
    @SerialName("conflict")
    data class Conflict(
        override val key: String,
        override val newVersion: Int,
        val serverValue: String,
        val serverVersion: Int,
        val serverModifiedAt: String,
    ) : SettingSyncResultDto()
}
