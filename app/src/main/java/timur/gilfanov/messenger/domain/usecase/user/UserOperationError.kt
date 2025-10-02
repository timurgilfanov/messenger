package timur.gilfanov.messenger.domain.usecase.user

import kotlin.time.Duration

sealed interface UserOperationError {
    data object NetworkNotAvailable : UserOperationError
    data object RemoteUnreachable : UserOperationError
    data object RemoteError : UserOperationError
    data class RateLimitExceeded(val waitFor: Duration) : UserOperationError
    data object UserNotFound : UserOperationError
    data object NoPermission : UserOperationError
}
