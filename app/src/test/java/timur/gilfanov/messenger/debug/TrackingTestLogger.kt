package timur.gilfanov.messenger.debug

import timur.gilfanov.messenger.util.Logger

/**
 * Test logger that tracks log messages for verification in tests.
 */
class TrackingTestLogger : Logger {
    private val _debugLogs = mutableListOf<String>()
    private val _infoLogs = mutableListOf<String>()
    private val _warnLogs = mutableListOf<String>()
    private val _errorLogs = mutableListOf<String>()

    val debugLogs: List<String> = _debugLogs
    val infoLogs: List<String> = _infoLogs
    val warnLogs: List<String> = _warnLogs
    val errorLogs: List<String> = _errorLogs

    override fun d(tag: String, message: String) {
        _debugLogs.add(message)
        println("DEBUG/$tag: $message")
    }

    override fun i(tag: String, message: String) {
        _infoLogs.add(message)
        println("INFO/$tag: $message")
    }

    override fun w(tag: String, message: String) {
        _warnLogs.add(message)
        println("WARN/$tag: $message")
    }

    override fun w(tag: String, message: String, throwable: Throwable) {
        _warnLogs.add(message)
        println("WARN/$tag: $message")
        throwable.printStackTrace()
    }

    override fun e(tag: String, message: String) {
        _errorLogs.add(message)
        println("ERROR/$tag: $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable) {
        _errorLogs.add(message)
        println("ERROR/$tag: $message")
        throwable.printStackTrace()
    }

    fun clearLogs() {
        _debugLogs.clear()
        _infoLogs.clear()
        _warnLogs.clear()
        _errorLogs.clear()
    }
}
