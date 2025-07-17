package timur.gilfanov.messenger.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {

    companion object {
        private var isMainDispatcherSet = false
        private var globalTestDispatcher: TestDispatcher? = null

        init {
            // Set up Main dispatcher globally for all tests
            val dispatcher = StandardTestDispatcher()
            try {
                Dispatchers.setMain(dispatcher)
                isMainDispatcherSet = true
                globalTestDispatcher = dispatcher
            } catch (e: Exception) {
                // Main dispatcher might already be set
                println("MainDispatcherRule: Could not set global Main dispatcher: ${e.message}")
            }
        }
    }

    override fun starting(description: Description) {
        if (!isMainDispatcherSet) {
            Dispatchers.setMain(testDispatcher)
        }
    }

    override fun finished(description: Description) {
        // Ensure all coroutines are completed before resetting
        testDispatcher.scheduler.advanceUntilIdle()
        globalTestDispatcher?.scheduler?.advanceUntilIdle()
        // Don't reset Main dispatcher - leave it set for background coroutines
    }
}
