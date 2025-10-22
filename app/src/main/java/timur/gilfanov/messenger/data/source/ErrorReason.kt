package timur.gilfanov.messenger.data.source

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

/**
 * Extracts an [ErrorReason] from a [Throwable] for logging purposes.
 *
 * Converts the exception to a human-readable reason by extracting:
 * 1. The exception's message if available
 * 2. The exception's class name if message is null
 * 3. "Unnamed exception" as a fallback
 *
 * Example:
 * ```
 * try {
 *     dataSource.fetchData()
 * } catch (e: IOException) {
 *     return Failure(DataSourceError(e.errorReason))
 * }
 * ```
 */
val Throwable.errorReason: ErrorReason
    get() = ErrorReason(this.message ?: this::class.simpleName ?: "Unnamed exception")
