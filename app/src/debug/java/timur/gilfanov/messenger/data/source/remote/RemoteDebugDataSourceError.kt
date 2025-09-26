package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Duration

sealed interface RemoteDebugDataSourceError {
    data object NetworkNotAvailable : RemoteDebugDataSourceError
    data object ServerUnreachable : RemoteDebugDataSourceError
    data object ServerError : RemoteDebugDataSourceError
    data object Unauthorized : RemoteDebugDataSourceError
    data class CooldownActive(val remaining: Duration) : RemoteDebugDataSourceError
    data object RateLimitExceeded : RemoteDebugDataSourceError
}

sealed interface GetChatsError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : GetChatsError
    data class UnknownError(val cause: Throwable) : GetChatsError
}

sealed interface AddMessageError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : AddMessageError
    data class UnknownError(val cause: Throwable) : AddMessageError
}

sealed interface AddChatError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : AddChatError
    data class UnknownError(val cause: Throwable) : AddChatError
}

sealed interface ClearDataError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : ClearDataError
    data class UnknownError(val cause: Throwable) : ClearDataError
}
