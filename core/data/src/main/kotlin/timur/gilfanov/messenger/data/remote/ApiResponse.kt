package timur.gilfanov.messenger.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: ApiError? = null,
    val success: Boolean = true,
)

@Serializable
data class ApiError(val code: String, val message: String, val details: Map<String, String>? = null)
