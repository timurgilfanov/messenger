package timur.gilfanov.messenger.util

/**
 * Abstraction for logging to decouple from Android framework dependencies.
 */
interface Logger {
    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String)

    /**
     * Log an info message.
     */
    fun i(tag: String, message: String)

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String)

    /**
     * Log a warning message with an exception.
     */
    fun w(tag: String, message: String, throwable: Throwable)

    /**
     * Log an error message.
     */
    fun e(tag: String, message: String)

    /**
     * Log an error message with an exception.
     */
    fun e(tag: String, message: String, throwable: Throwable)
}
