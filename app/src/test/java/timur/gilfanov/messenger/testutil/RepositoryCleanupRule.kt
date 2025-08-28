package timur.gilfanov.messenger.testutil

import java.io.Closeable
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.util.Logger

class RepositoryCleanupRule(
    private val repositoryProvider: () -> Any?,
    private val logger: Logger = NoOpLogger(),
) : TestWatcher() {

    companion object {
        private const val TAG = "RepositoryRule"
    }

    override fun starting(description: Description) {
        logger.d(TAG, "Starting test: ${description.methodName}")
    }

    override fun finished(description: Description) {
        logger.d(TAG, "Finishing test: ${description.methodName}")
        val repository = repositoryProvider()
        if (repository is Closeable) {
            try {
                repository.close()
                logger.d(TAG, "Repository closed for test: ${description.methodName}")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to close repository for test: ${description.methodName}", e)
            }
        } else {
            logger.w(TAG, "Repository is not Closeable for test: ${description.methodName}")
        }
    }

    override fun failed(e: Throwable, description: Description) {
        logger.e(TAG, "Test failed: ${description.methodName}", e)
    }
}
