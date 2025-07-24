@file:OptIn(ExperimentalFoundationApi::class)

package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import timur.gilfanov.messenger.R

data class SwipeAction(
    val icon: ImageVector,
    val labelRes: Int? = null,
    val backgroundColor: Color,
    val iconTint: Color,
    val onClick: () -> Unit,
    @Deprecated("Use labelRes instead for proper internationalization")
    val label: String? = null,
)

enum class SwipeState {
    Closed,
    StartActions,
    EndActions,
}

private object SwipeActionDimensions {
    val STANDARD_BUTTON_WIDTH = 72.dp
    val COMPACT_BUTTON_WIDTH = 60.dp
    const val MAX_STANDARD_ACTIONS = 2
    const val MAX_COMPACT_ACTIONS = 3
}

@Composable
fun SwipeToActionRow(
    modifier: Modifier = Modifier,
    startActions: List<SwipeAction> = emptyList(),
    endActions: List<SwipeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val startActionButtonWidth = getActionButtonWidth(startActions.size)
    val endActionButtonWidth = getActionButtonWidth(endActions.size)
    val startActionsWidth = with(density) { (startActions.size * startActionButtonWidth.toPx()) }
    val endActionsWidth = with(density) { (endActions.size * endActionButtonWidth.toPx()) }

    val anchors = createAnchors(startActions, endActions, startActionsWidth, endActionsWidth)
    val anchoredDraggableState = createDraggableState(anchors, density)

    SwipeContainer(
        state = SwipeContainerState(
            startActions = startActions,
            endActions = endActions,
            startActionButtonWidth = startActionButtonWidth,
            endActionButtonWidth = endActionButtonWidth,
            anchoredDraggableState = anchoredDraggableState,
            hapticFeedback = hapticFeedback,
        ),
        modifier = modifier,
        content = content,
    )
}

private fun createAnchors(
    startActions: List<SwipeAction>,
    endActions: List<SwipeAction>,
    startActionsWidth: Float,
    endActionsWidth: Float,
) = DraggableAnchors {
    SwipeState.Closed at 0f
    if (startActions.isNotEmpty()) {
        SwipeState.StartActions at startActionsWidth
    }
    if (endActions.isNotEmpty()) {
        SwipeState.EndActions at -endActionsWidth
    }
}

@Composable
private fun createDraggableState(
    anchors: DraggableAnchors<SwipeState>,
    density: androidx.compose.ui.unit.Density,
) = remember {
    AnchoredDraggableState(
        initialValue = SwipeState.Closed,
        anchors = anchors,
        positionalThreshold = { distance: Float -> distance * 0.5f },
        velocityThreshold = { with(density) { 100.dp.toPx() } },
        snapAnimationSpec = tween(),
        decayAnimationSpec = exponentialDecay(),
    )
}

data class SwipeContainerState(
    val startActions: List<SwipeAction>,
    val endActions: List<SwipeAction>,
    val startActionButtonWidth: Dp,
    val endActionButtonWidth: Dp,
    val anchoredDraggableState: AnchoredDraggableState<SwipeState>,
    val hapticFeedback: HapticFeedback,
)

private fun getActionButtonWidth(actionCount: Int): Dp = when {
    actionCount == 0 -> 0.dp
    actionCount <= SwipeActionDimensions.MAX_STANDARD_ACTIONS -> SwipeActionDimensions.STANDARD_BUTTON_WIDTH
    actionCount <= SwipeActionDimensions.MAX_COMPACT_ACTIONS -> SwipeActionDimensions.COMPACT_BUTTON_WIDTH
    else -> SwipeActionDimensions.COMPACT_BUTTON_WIDTH // Fallback for more than 3 actions
}

@Composable
private fun SwipeContainer(
    state: SwipeContainerState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        ActionButtons(
            startActions = state.startActions,
            endActions = state.endActions,
            startActionButtonWidth = state.startActionButtonWidth,
            endActionButtonWidth = state.endActionButtonWidth,
            hapticFeedback = state.hapticFeedback,
        )

        SwipeableContent(
            anchoredDraggableState = state.anchoredDraggableState,
            content = content,
        )
    }
}

@Composable
private fun SwipeActionButton(action: SwipeAction, width: Dp, hapticFeedback: HapticFeedback) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.backgroundColor)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                action.onClick()
            },
        contentAlignment = Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.labelRes?.let { stringResource(it) } ?: action.label,
            tint = action.iconTint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BoxScope.ActionButtons(
    startActions: List<SwipeAction>,
    endActions: List<SwipeAction>,
    startActionButtonWidth: Dp,
    endActionButtonWidth: Dp,
    hapticFeedback: HapticFeedback,
) {
    if (startActions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(startActionButtonWidth * startActions.size)
                .align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.End,
        ) {
            startActions.forEach { action ->
                SwipeActionButton(
                    action = action,
                    width = startActionButtonWidth,
                    hapticFeedback = hapticFeedback,
                )
            }
        }
    }

    if (endActions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(endActionButtonWidth * endActions.size)
                .align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.Start,
        ) {
            endActions.forEach { action ->
                SwipeActionButton(
                    action = action,
                    width = endActionButtonWidth,
                    hapticFeedback = hapticFeedback,
                )
            }
        }
    }
}

@Composable
private fun SwipeableContent(
    anchoredDraggableState: AnchoredDraggableState<SwipeState>,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                IntOffset(
                    x = anchoredDraggableState
                        .offset
                        .roundToInt(),
                    y = 0,
                )
            }
            .anchoredDraggable(
                state = anchoredDraggableState,
                orientation = Orientation.Horizontal,
            ),
    ) {
        content()
    }
}

@Preview(name = "Basic - Archive & Delete", heightDp = 72)
@Composable
private fun SwipeToActionRowBasicPreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Archive ← | → Delete")
    }
}

@Preview(name = "Multiple Actions - 3 on each side", heightDp = 72)
@Composable
private fun SwipeToActionRowMultiplePreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Settings,
                labelRes = R.string.chat_list_swipe_mute_content_description,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.MoreVert,
                labelRes = R.string.chat_list_swipe_more_content_description,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Notifications,
                labelRes = R.string.chat_list_swipe_notifications_content_description,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Pin, Mute, Archive ← | → More, Notify, Delete")
    }
}

@Preview(name = "Start Actions Only", heightDp = 72)
@Composable
private fun SwipeToActionRowStartOnlyPreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Archive, Pin ← (swipe right only)")
    }
}

@Preview(name = "End Actions Only", heightDp = 72)
@Composable
private fun SwipeToActionRowEndOnlyPreview() {
    SwipeToActionRow(
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("(swipe left only) → Delete")
    }
}

@Preview(name = "No Actions", heightDp = 72)
@Composable
private fun SwipeToActionRowNoActionsPreview() {
    SwipeToActionRow {
        PreviewContent("No swipe actions (static content)")
    }
}

@Preview(
    name = "Dark Mode - Multiple Actions",
    heightDp = 72,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SwipeToActionRowDarkModePreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Dark Mode: Pin, Archive ← | → Delete")
    }
}

@Preview(
    name = "Dark Mode - 3 Actions Each Side",
    heightDp = 72,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SwipeToActionRowDarkModeMultiplePreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Settings,
                labelRes = R.string.chat_list_swipe_mute_content_description,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.MoreVert,
                labelRes = R.string.chat_list_swipe_more_content_description,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Notifications,
                labelRes = R.string.chat_list_swipe_notifications_content_description,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Dark Mode: 3 + 3 actions (60dp width)")
    }
}

@Preview(name = "Narrow Display - 3 Actions", heightDp = 72, widthDp = 300)
@Composable
private fun SwipeToActionRowNarrowPreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Narrow (300dp): Pin, Archive ← | → Delete")
    }
}

@Preview(name = "Very Narrow - Single Actions", heightDp = 72, widthDp = 250)
@Composable
private fun SwipeToActionRowVeryNarrowPreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Home,
                labelRes = R.string.chat_list_swipe_archive_content_description,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Very Narrow (250dp): Archive ← | → Delete")
    }
}

@Preview(name = "Extra Narrow - End Only", heightDp = 72, widthDp = 200)
@Composable
private fun SwipeToActionRowExtraNarrowPreview() {
    SwipeToActionRow(
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Extra Narrow (200dp): → Delete only")
    }
}

@Preview(
    name = "Dark + Narrow - Multiple",
    heightDp = 72,
    widthDp = 280,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SwipeToActionRowDarkNarrowPreview() {
    SwipeToActionRow(
        startActions = listOf(
            SwipeAction(
                icon = Icons.Default.Star,
                labelRes = R.string.chat_list_swipe_pin_content_description,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {},
            ),
        ),
        endActions = listOf(
            SwipeAction(
                icon = Icons.Default.Settings,
                labelRes = R.string.chat_list_swipe_settings_content_description,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {},
            ),
            SwipeAction(
                icon = Icons.Default.Delete,
                labelRes = R.string.chat_list_swipe_delete_content_description,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {},
            ),
        ),
    ) {
        PreviewContent("Dark + Narrow: Pin ← | → Settings, Delete")
    }
}

@Composable
private fun PreviewContent(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Center),
        )
    }
}
