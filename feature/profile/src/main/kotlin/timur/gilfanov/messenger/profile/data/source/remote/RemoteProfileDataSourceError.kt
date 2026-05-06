package timur.gilfanov.messenger.profile.data.source.remote

import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors specific to remote profile operations.
 */
sealed interface RemoteProfileDataSourceError {
    sealed interface Authentication : RemoteProfileDataSourceError {
        data object TokenMissing : Authentication
        data object TokenExpired : Authentication
        data object TokenInvalid : Authentication
        data object SessionRevoked : Authentication
    }

    data object InsufficientPermissions : RemoteProfileDataSourceError

    data class RemoteDataSource(val error: RemoteDataSourceError) : RemoteProfileDataSourceError
}

fun RemoteProfileDataSourceError.toRemoteError(): RemoteError = when (this) {
    RemoteProfileDataSourceError.Authentication.SessionRevoked,
    RemoteProfileDataSourceError.Authentication.TokenInvalid,
    RemoteProfileDataSourceError.Authentication.TokenMissing,
    RemoteProfileDataSourceError.Authentication.TokenExpired,
    -> RemoteError.Unauthenticated

    RemoteProfileDataSourceError.InsufficientPermissions ->
        RemoteError.InsufficientPermissions

    is RemoteProfileDataSourceError.RemoteDataSource ->
        when (error) {
            is RemoteDataSourceError.CooldownActive ->
                RemoteError.Failed.Cooldown(error.remaining)

            RemoteDataSourceError.RateLimitExceeded,
            RemoteDataSourceError.ServerError,
            RemoteDataSourceError.ServiceUnavailable.ServerUnreachable,
            ->
                RemoteError.Failed.ServiceDown

            RemoteDataSourceError.ServiceUnavailable.Timeout ->
                RemoteError.UnknownStatus.ServiceTimeout

            RemoteDataSourceError.ServiceUnavailable.NetworkNotAvailable ->
                RemoteError.Failed.NetworkNotAvailable

            is RemoteDataSourceError.UnknownServiceError ->
                RemoteError.Failed.UnknownServiceError(cause = error.reason)
        }
}
