package timur.gilfanov.messenger.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

suspend fun <T> MutableStateFlow<T>.repeatOnSubscription(
    stopTimeout: Duration = 5.seconds,
    block: suspend CoroutineScope.() -> Unit,
): Unit = coroutineScope {
    var blockJob: Job? = null
    var stopJob: Job? = null
    subscriptionCount
        .map { it > 0 }
        .distinctUntilChanged()
        .collect { hasSubscribers ->
            if (hasSubscribers) {
                stopJob?.cancel()
                stopJob = null
                if (blockJob?.isActive != true) {
                    blockJob = launch(block = block)
                }
            } else {
                stopJob = launch {
                    delay(stopTimeout)
                    blockJob?.cancel()
                    blockJob = null
                }
            }
        }
}
