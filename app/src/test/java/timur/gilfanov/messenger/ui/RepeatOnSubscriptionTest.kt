package timur.gilfanov.messenger.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RepeatOnSubscriptionTest {

    @Test
    fun `block starts when subscriber appears`() = runTest {
        val state = MutableStateFlow(0)
        var blockStartCount = 0

        val monitorJob = launch {
            state.repeatOnSubscription {
                blockStartCount++
            }
        }
        runCurrent()
        assertEquals(0, blockStartCount)

        val collectorJob = launch { state.collect {} }
        runCurrent()
        assertEquals(1, blockStartCount)

        collectorJob.cancelAndJoin()
        monitorJob.cancelAndJoin()
    }

    @Test
    fun `block is cancelled after stop timeout when subscribers disappear`() = runTest {
        val state = MutableStateFlow(0)
        var blockActive = false
        val stopTimeout = 5.seconds

        val monitorJob = launch {
            state.repeatOnSubscription(stopTimeout) {
                blockActive = true
                try {
                    awaitCancellation()
                } finally {
                    blockActive = false
                }
            }
        }
        runCurrent()

        val collectorJob = launch { state.collect {} }
        runCurrent()
        assertTrue(blockActive)

        collectorJob.cancelAndJoin()
        runCurrent()
        assertTrue(blockActive)

        advanceTimeBy(stopTimeout)
        runCurrent()
        assertTrue(!blockActive)

        monitorJob.cancelAndJoin()
    }

    @Test
    fun `block is not cancelled if subscriber reappears before timeout`() = runTest {
        val state = MutableStateFlow(0)
        var blockActive = false
        var blockStartCount = 0
        val stopTimeout = 5.seconds

        val monitorJob = launch {
            state.repeatOnSubscription(stopTimeout) {
                blockStartCount++
                blockActive = true
                try {
                    awaitCancellation()
                } finally {
                    blockActive = false
                }
            }
        }
        runCurrent()

        val collectorJob1 = launch { state.collect {} }
        runCurrent()
        assertTrue(blockActive)
        assertEquals(1, blockStartCount)

        collectorJob1.cancelAndJoin()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(blockActive)

        val collectorJob2 = launch { state.collect {} }
        runCurrent()
        assertTrue(blockActive)
        assertEquals(1, blockStartCount)

        advanceTimeBy(stopTimeout)
        runCurrent()
        assertTrue(blockActive)

        collectorJob2.cancelAndJoin()
        monitorJob.cancelAndJoin()
    }

    @Test
    fun `block restarts after being cancelled and subscribers reappear`() = runTest {
        val state = MutableStateFlow(0)
        var blockStartCount = 0
        val stopTimeout = 5.seconds

        val monitorJob = launch {
            state.repeatOnSubscription(stopTimeout) {
                blockStartCount++
                awaitCancellation()
            }
        }
        runCurrent()

        val collectorJob1 = launch { state.collect {} }
        runCurrent()
        assertEquals(1, blockStartCount)

        collectorJob1.cancelAndJoin()
        runCurrent()
        advanceTimeBy(stopTimeout)
        runCurrent()

        val collectorJob2 = launch { state.collect {} }
        runCurrent()
        assertEquals(2, blockStartCount)

        collectorJob2.cancelAndJoin()
        monitorJob.cancelAndJoin()
    }

    @Test
    fun `block does not start without subscribers`() = runTest {
        val state = MutableStateFlow(0)
        var blockStartCount = 0

        val monitorJob = launch {
            state.repeatOnSubscription {
                blockStartCount++
            }
        }
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(0, blockStartCount)

        monitorJob.cancelAndJoin()
    }

    @Test
    fun `multiple subscribers keep block alive`() = runTest {
        val state = MutableStateFlow(0)
        var blockActive = false
        var blockStartCount = 0
        val stopTimeout = 5.seconds

        val monitorJob = launch {
            state.repeatOnSubscription(stopTimeout) {
                blockStartCount++
                blockActive = true
                try {
                    awaitCancellation()
                } finally {
                    blockActive = false
                }
            }
        }
        runCurrent()

        val collectors = mutableListOf<Job>()
        collectors += launch { state.collect {} }
        collectors += launch { state.collect {} }
        runCurrent()
        assertTrue(blockActive)
        assertEquals(1, blockStartCount)

        collectors.removeFirst().cancelAndJoin()
        runCurrent()
        advanceTimeBy(stopTimeout)
        runCurrent()
        assertTrue(blockActive)

        collectors.removeFirst().cancelAndJoin()
        runCurrent()
        advanceTimeBy(stopTimeout)
        runCurrent()
        assertTrue(!blockActive)

        monitorJob.cancelAndJoin()
    }
}
