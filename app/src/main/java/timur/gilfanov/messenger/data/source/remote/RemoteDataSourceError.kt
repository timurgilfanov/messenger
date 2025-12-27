package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Duration

// todo replace with specific errors for data source operation like in
//  UpdateNameRemoteDataSourceError.kt with logical and datasource related errors
sealed interface RemoteDataSourceError {
    data object NetworkNotAvailable : RemoteDataSourceError
    data object ServerUnreachable : RemoteDataSourceError
    data object ServerError : RemoteDataSourceError
    data object Unauthorized : RemoteDataSourceError
    data object ChatNotFound : RemoteDataSourceError
    data object MessageNotFound : RemoteDataSourceError
    data object InvalidInviteLink : RemoteDataSourceError
    data object ExpiredInviteLink : RemoteDataSourceError
    data object ChatClosed : RemoteDataSourceError
    data object AlreadyJoined : RemoteDataSourceError
    data object ChatFull : RemoteDataSourceError
    data object UserBlocked : RemoteDataSourceError
    data class CooldownActive(val remaining: Duration) : RemoteDataSourceError
    data object RateLimitExceeded : RemoteDataSourceError
    data class UnknownError(val cause: Throwable) : RemoteDataSourceError
}
