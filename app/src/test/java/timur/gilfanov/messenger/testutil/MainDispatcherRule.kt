package timur.gilfanov.messenger.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
        println(
            "[${Thread.currentThread().name}] MainDispatcherRule starting: " +
                "${description.className}.${description.methodName}",
        )
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        println(
            "[${Thread.currentThread().name}] MainDispatcherRule finished: " +
                "${description.className}.${description.methodName}",
        )
    }
}
