package timur.gilfanov.messenger.data.remote

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface RemoteDataSourceError {
    sealed interface ServiceUnavailable : RemoteDataSourceError {
        data object NetworkNotAvailable : ServiceUnavailable
        data object ServerUnreachable : ServiceUnavailable
        data object Timeout : ServiceUnavailable
    }
    data object ServerError : RemoteDataSourceError
    data object RateLimitExceeded : RemoteDataSourceError
    data class CooldownActive(val remaining: Duration) : RemoteDataSourceError
    data class UnknownServiceError(val reason: ErrorReason) : RemoteDataSourceError
}

fun RemoteDataSourceError.toRemoteError(): RemoteError = when (this) {
    RemoteDataSourceError.ServiceUnavailable.NetworkNotAvailable ->
        RemoteError.Failed.NetworkNotAvailable
    RemoteDataSourceError.ServiceUnavailable.ServerUnreachable,
    RemoteDataSourceError.ServerError,
    RemoteDataSourceError.RateLimitExceeded,
    -> RemoteError.Failed.ServiceDown
    RemoteDataSourceError.ServiceUnavailable.Timeout -> RemoteError.UnknownStatus.ServiceTimeout
    is RemoteDataSourceError.CooldownActive -> RemoteError.Failed.Cooldown(remaining)
    is RemoteDataSourceError.UnknownServiceError -> RemoteError.Failed.UnknownServiceError(reason)
}
