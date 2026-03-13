package timur.gilfanov.messenger

import timur.gilfanov.messenger.util.Logger

/**
 * No-operation implementation of [timur.gilfanov.messenger.util.Logger] for tests.
 * Discards all log messages to keep tests fast and focused on behavior.
 */
class NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit

    override fun i(tag: String, message: String) = Unit

    override fun w(tag: String, message: String) = Unit

    override fun w(tag: String, message: String, throwable: Throwable) = Unit

    override fun e(tag: String, message: String) = Unit

    override fun e(tag: String, message: String, throwable: Throwable) = Unit
}
