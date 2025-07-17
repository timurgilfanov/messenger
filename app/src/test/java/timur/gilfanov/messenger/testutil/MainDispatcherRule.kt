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
    }

    override fun starting(description: Description) {
        if (!isMainDispatcherSet) {
            Dispatchers.setMain(testDispatcher)
            isMainDispatcherSet = true
        }
    }

    // No cleanup - let Main dispatcher persist for entire test suite
    // This prevents "Dispatchers.Main was accessed when platform dispatcher was absent"
    // errors from background coroutines in Orbit MVI framework
}
