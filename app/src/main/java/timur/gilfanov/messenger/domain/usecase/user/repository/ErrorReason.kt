package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * A wrapper for error reason messages from data source operations.
 *
 * This value class wraps error messages as strings instead of storing [Throwable] directly
 * to ensure value-based equality semantics. When [Throwable] instances were used directly
 * in error data classes, equality checks failed because [Throwable] uses reference equality.
 *
 * ## Usage Constraints
 * **IMPORTANT**: [ErrorReason] is intended **only for logging and debugging purposes**.
 * Do NOT parse or extract values from [ErrorReason] to affect error handling behavior.
 *
 * If you need to make decisions based on error types or extract structured data,
 * the error taxonomy must be reviewed and extended with proper data objects or
 * data classes that capture the required information.
 *
 * @property value The human-readable error reason message for logging
 */
@JvmInline
value class ErrorReason(val value: String)
