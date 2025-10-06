package timur.gilfanov.messenger.domain.usecase.user

/**
 * Errors specific to profile retrieval operations.
 *
 * Currently does not define operation-specific errors, inheriting only
 * common errors from [UserOperationError] such as:
 * - Network/Service errors ([UserOperationError.ServiceUnavailable])
 * - Authentication errors ([UserOperationError.Unauthorized])
 * - User existence errors ([UserOperationError.UserNotFound])
 * - Rate limiting ([UserOperationError.RateLimitExceeded])
 */
sealed interface GetProfileError : UserOperationError
