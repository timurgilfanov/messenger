package timur.gilfanov.messenger

import timur.gilfanov.messenger.util.Logger

/**
 * Simple logger implementation for tests.
 * Logs to system out for test debugging.
 */
class TestLogger : Logger {
    override fun d(tag: String, message: String) {
        println("DEBUG/$tag: $message")
    }

    override fun i(tag: String, message: String) {
        println("INFO/$tag: $message")
    }

    override fun w(tag: String, message: String) {
        println("WARN/$tag: $message")
    }

    override fun w(tag: String, message: String, throwable: Throwable) {
        println("WARN/$tag: $message")
        throwable.printStackTrace()
    }

    override fun e(tag: String, message: String) {
        println("ERROR/$tag: $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable) {
        println("ERROR/$tag: $message")
        throwable.printStackTrace()
    }
}
