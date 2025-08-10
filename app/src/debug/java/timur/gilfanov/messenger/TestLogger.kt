package timur.gilfanov.messenger

import timur.gilfanov.messenger.util.Logger

/**
 * Simple logger implementation for tests.
 * Logs to system out for test debugging.
 */
class TestLogger : Logger {
    private val startTime = System.currentTimeMillis()
    private val time
        get() = "${System.currentTimeMillis() - startTime}"

    private val coroutine
        get() = Thread.currentThread().name

    override fun d(tag: String, message: String) {
        println("[$time $coroutine] DEBUG/$tag: $message")
    }

    override fun i(tag: String, message: String) {
        println("[$time $coroutine] INFO/$tag: $message")
    }

    override fun w(tag: String, message: String) {
        println("[$time $coroutine] WARN/$tag: $message")
    }

    override fun w(tag: String, message: String, throwable: Throwable) {
        println("[$time $coroutine] WARN/$tag: $message")
        throwable.printStackTrace()
    }

    override fun e(tag: String, message: String) {
        println("[$time $coroutine] ERROR/$tag: $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable) {
        println("[$time $coroutine] ERROR/$tag: $message")
        throwable.printStackTrace()
    }
}
