package timur.gilfanov.messenger.domain.usecase.user

import kotlin.time.Duration

sealed interface UserOperationError {
    sealed interface ServiceUnavailable : UserOperationError {
        data object NoConnectivity : ServiceUnavailable
        data object ServiceDown : ServiceUnavailable
        data object Timeout : ServiceUnavailable
    }

    data class RateLimitExceeded(val waitFor: Duration) : UserOperationError
    data object UserNotFound : UserOperationError
    data object Unauthorized : UserOperationError
    data class UnknownServiceError(val reason: String) : UserOperationError
    data object LocalDataCorrupted : UserOperationError
}
