package timur.gilfanov.messenger.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

suspend fun <T> MutableStateFlow<T>.repeatOnSubscription(
    stopTimeout: Duration = 5.seconds,
    block: suspend CoroutineScope.() -> Unit,
) {
    coroutineScope {
        var blockJob: Job? = null
        subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .collectLatest { hasSubscribers ->
                if (hasSubscribers) {
                    if (blockJob?.isActive != true) {
                        blockJob = launch(block = block)
                    }
                } else {
                    delay(stopTimeout)
                    blockJob?.cancel()
                    blockJob = null
                }
            }
    }
}
