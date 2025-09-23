package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Duration

sealed interface RemoteDebugDataSourceError {
    object NetworkNotAvailable : RemoteDebugDataSourceError
    object ServerUnreachable : RemoteDebugDataSourceError
    object ServerError : RemoteDebugDataSourceError
    object Unauthorized : RemoteDebugDataSourceError
    data class CooldownActive(val remaining: Duration) : RemoteDebugDataSourceError
    object RateLimitExceeded : RemoteDebugDataSourceError
}
sealed interface GetChatsError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : GetChatsError
    data class UnknownError(val cause: Throwable) : GetChatsError
}

sealed interface AddMessageError {
    data class RemoteError(val error: RemoteDebugDataSourceError) : AddMessageError
    data class UnknownError(val cause: Throwable) : AddMessageError
}
