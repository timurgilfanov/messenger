package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
@Serializable
data class SettingSyncResultDto(
    val key: String,
    val status: SyncStatusDto,
    val newVersion: Int,
    val serverValue: String? = null,
    val serverVersion: Int? = null,
    val serverModifiedAt: String? = null,
)

/**
 * Sync status for a single setting.
 */
@Serializable
enum class SyncStatusDto {
    @SerialName("success")
    SUCCESS,

    @SerialName("conflict")
    CONFLICT,
}
