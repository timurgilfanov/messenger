package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun SwipeToActionRow(
    modifier: Modifier = Modifier,
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {},
    startActionEnabled: Boolean = true,
    endActionEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (startActionEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartAction()
                    }
                    false // Don't dismiss, just perform action
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (endActionEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEndAction()
                    }
                    false // Don't dismiss, just perform action
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        enableDismissFromStartToEnd = startActionEnabled,
        enableDismissFromEndToStart = endActionEnabled,
        backgroundContent = {
            SwipeBackground(
                dismissDirection = swipeState.dismissDirection,
                startActionEnabled = startActionEnabled,
                endActionEnabled = endActionEnabled,
            )
        },
        content = { content() },
    )
}

@Composable
private fun SwipeBackground(
    dismissDirection: SwipeToDismissBoxValue,
    startActionEnabled: Boolean,
    endActionEnabled: Boolean,
) {
    val color = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> {
            if (startActionEnabled) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color.Transparent
            }
        }
        SwipeToDismissBoxValue.EndToStart -> {
            if (endActionEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            }
        }
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 16.dp),
        contentAlignment = when (dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            SwipeToDismissBoxValue.Settled -> Alignment.Center
        },
    ) {
        when (dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (startActionEnabled) {
                    ActionIcon(
                        icon = Icons.Default.Delete,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        backgroundColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (endActionEnabled) {
                    // Placeholder for future actions
                    // Could be archive, pin, etc.
                }
            }
            SwipeToDismissBoxValue.Settled -> {
                // No action icon when settled
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
