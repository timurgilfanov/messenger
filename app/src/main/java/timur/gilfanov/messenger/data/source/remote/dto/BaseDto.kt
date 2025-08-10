package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

/**
 * Base DTOs for network communication.
 */

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: ErrorResponseDto? = null,
    val success: Boolean = true,
)

@Serializable(with = ErrorResponseDtoSerializer::class)
data class ErrorResponseDto(
    val code: ApiErrorCode,
    val message: String,
    val details: Map<String, String>? = null,
)

@Serializable
data class PaginationDto(val page: Int, val size: Int, val hasNext: Boolean)
