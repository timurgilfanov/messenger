package timur.gilfanov.messenger.util

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RepeatOnSubscriptionUnitTest {

    companion object {
        private val STOP_TIMEOUT = 1.seconds
        private val WITHIN_TIMEOUT = 500.milliseconds
        private val LONG_ADVANCE = 10.seconds
    }

    private fun TestScope.subscribe(flow: MutableStateFlow<Int>): Job =
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect {}
        }

    private fun TestScope.launchRepeatOnSubscription(
        flow: MutableStateFlow<Int>,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = backgroundScope.launch {
        flow.repeatOnSubscription(stopTimeout = STOP_TIMEOUT, block = block)
    }

    @Test
    fun `block starts when first subscriber appears`() = runTest {
        val flow = MutableStateFlow(0)
        val startCount = AtomicInteger(0)

        launchRepeatOnSubscription(flow) {
            startCount.incrementAndGet()
            awaitCancellation()
        }
        runCurrent()
        assertEquals(0, startCount.get())

        val sub = subscribe(flow)
        runCurrent()
        assertEquals(1, startCount.get())

        sub.cancel()
    }

    @Test
    fun `block is not restarted after self-completing while subscribers are present`() = runTest {
        val flow = MutableStateFlow(0)
        val startCount = AtomicInteger(0)

        launchRepeatOnSubscription(flow) {
            startCount.incrementAndGet()
        }

        val sub = subscribe(flow)
        runCurrent()
        assertEquals(1, startCount.get())

        advanceTimeBy(LONG_ADVANCE)
        assertEquals(1, startCount.get())

        sub.cancel()
    }

    @Test
    fun `block keeps running while subscribers are active`() = runTest {
        val flow = MutableStateFlow(0)
        var blockRunning = false

        launchRepeatOnSubscription(flow) {
            blockRunning = true
            try {
                awaitCancellation()
            } finally {
                blockRunning = false
            }
        }

        val sub = subscribe(flow)
        runCurrent()

        advanceTimeBy(LONG_ADVANCE)
        assertTrue(blockRunning)

        sub.cancel()
    }

    @Test
    fun `block is not cancelled immediately when subscribers leave`() = runTest {
        val flow = MutableStateFlow(0)
        var blockRunning = false

        launchRepeatOnSubscription(flow) {
            blockRunning = true
            try {
                awaitCancellation()
            } finally {
                blockRunning = false
            }
        }

        val sub = subscribe(flow)
        runCurrent()

        sub.cancel()
        runCurrent()

        assertTrue(blockRunning)
    }

    @Test
    fun `block is cancelled after stopTimeout elapses with no subscribers`() = runTest {
        val flow = MutableStateFlow(0)
        var blockRunning = false

        launchRepeatOnSubscription(flow) {
            blockRunning = true
            try {
                awaitCancellation()
            } finally {
                blockRunning = false
            }
        }

        val sub = subscribe(flow)
        runCurrent()

        sub.cancel()
        runCurrent()

        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertFalse(blockRunning)
    }

    @Test
    fun `block keeps running if subscribers return within stopTimeout`() = runTest {
        val flow = MutableStateFlow(0)
        val startCount = AtomicInteger(0)
        var blockRunning = false

        launchRepeatOnSubscription(flow) {
            startCount.incrementAndGet()
            blockRunning = true
            try {
                awaitCancellation()
            } finally {
                blockRunning = false
            }
        }

        val sub1 = subscribe(flow)
        runCurrent()

        sub1.cancel()
        runCurrent()

        advanceTimeBy(WITHIN_TIMEOUT)

        val sub2 = subscribe(flow)
        runCurrent()

        assertEquals(1, startCount.get())
        assertTrue(blockRunning)

        sub2.cancel()
    }

    @Test
    fun `block restarts if subscribers return after timeout`() = runTest {
        val flow = MutableStateFlow(0)
        val startCount = AtomicInteger(0)

        launchRepeatOnSubscription(flow) {
            startCount.incrementAndGet()
            awaitCancellation()
        }

        val sub1 = subscribe(flow)
        runCurrent()
        assertEquals(1, startCount.get())

        sub1.cancel()
        runCurrent()

        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        val sub2 = subscribe(flow)
        runCurrent()
        assertEquals(2, startCount.get())

        sub2.cancel()
    }

    @Test
    fun `block is cancelled when parent scope is cancelled`() = runTest {
        val flow = MutableStateFlow(0)
        var blockRunning = false

        val parentJob = launchRepeatOnSubscription(flow) {
            blockRunning = true
            try {
                awaitCancellation()
            } finally {
                blockRunning = false
            }
        }

        val sub = subscribe(flow)
        runCurrent()

        parentJob.cancel()
        runCurrent()

        assertFalse(blockRunning)

        sub.cancel()
    }
}
