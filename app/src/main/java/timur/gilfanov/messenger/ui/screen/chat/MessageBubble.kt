package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.ui.ExcludeFromInstrumentationTestCoverageReportGenerated
import timur.gilfanov.messenger.ui.theme.MessengerTheme

private const val TIMESTAMP_ALPHA = 0.7f
private const val CHECK_OVERLAP_SPACING_DP = -2

@Composable
fun MessageBubble(message: MessageUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        MessageBubbleContent(message = message)

        when (val status = message.deliveryStatus) {
            is DeliveryStatus.Failed -> {
                MessageError(
                    error = status.reason.toString(),
                    isFromCurrentUser = message.isFromCurrentUser,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun MessageBubbleContent(message: MessageUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromCurrentUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        MessageBubbleBox(message = message)
    }
}

@Composable
private fun MessageBubbleBox(message: MessageUiModel) {
    val maxBubbleWidth = 280.dp
    val bubbleCornerRadius = 16.dp
    val messageBubblePadding = 12.dp

    Box(
        modifier = Modifier
            .widthIn(max = maxBubbleWidth)
            .clip(
                RoundedCornerShape(
                    topStart = bubbleCornerRadius,
                    topEnd = bubbleCornerRadius,
                    bottomStart = if (message.isFromCurrentUser) bubbleCornerRadius else 4.dp,
                    bottomEnd = if (message.isFromCurrentUser) 4.dp else bubbleCornerRadius,
                ),
            )
            .background(
                if (message.isFromCurrentUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .padding(messageBubblePadding),
    ) {
        MessageBubbleText(message = message)
    }
}

@Composable
private fun MessageBubbleText(message: MessageUiModel) {
    Column {
        if (!message.isFromCurrentUser) {
            SenderNameText(message = message)
        }

        MessageContentText(message = message)

        MessageFooter(message = message)
    }
}

@Composable
private fun SenderNameText(message: MessageUiModel) {
    Text(
        text = message.senderName,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageContentText(message: MessageUiModel) {
    Text(
        text = message.text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (message.isFromCurrentUser) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun MessageFooter(message: MessageUiModel) {
    val timestampFontSize = 10.sp
    val timestampAlpha = TIMESTAMP_ALPHA

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = message.createdAt,
            style = MaterialTheme.typography.bodySmall,
            fontSize = timestampFontSize,
            color = if (message.isFromCurrentUser) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = timestampAlpha)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = timestampAlpha)
            },
        )

        if (message.isFromCurrentUser) {
            DeliveryStatusIndicator(
                status = message.deliveryStatus,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = timestampAlpha),
            )
        }
    }
}

@Composable
private fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is DeliveryStatus.Sending -> {
            CircularProgressIndicator(
                modifier = modifier.size(12.dp),
                strokeWidth = 1.dp,
                color = tint,
            )
        }

        DeliveryStatus.Delivered -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Delivered",
                modifier = modifier.size(12.dp),
                tint = tint,
            )
        }

        DeliveryStatus.Read -> {
            val checkOverlapSpacing = CHECK_OVERLAP_SPACING_DP.dp
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(checkOverlapSpacing),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Read",
                    modifier = Modifier.size(12.dp),
                    tint = tint,
                )
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Read",
                    modifier = Modifier.size(12.dp),
                    tint = tint,
                )
            }
        }

        is DeliveryStatus.Failed -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Failed",
                modifier = modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        else -> {}
    }
}

@Composable
private fun MessageError(error: String, isFromCurrentUser: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromCurrentUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true)
@ExcludeFromInstrumentationTestCoverageReportGenerated
@Composable
private fun MessageBubbleCurrentUserPreview() {
    MessengerTheme {
        MessageBubble(
            message = MessageUiModel(
                id = "1",
                text = "Hello! This is a message from the current user.",
                senderId = "current-user",
                senderName = "You",
                createdAt = "14:30",
                deliveryStatus = DeliveryStatus.Read,
                isFromCurrentUser = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromInstrumentationTestCoverageReportGenerated
@Composable
private fun MessageBubbleOtherUserPreview() {
    MessengerTheme {
        MessageBubble(
            message = MessageUiModel(
                id = "2",
                text = "Hi there! This is a message from another user.",
                senderId = "other-user",
                senderName = "Alice",
                createdAt = "14:28",
                deliveryStatus = DeliveryStatus.Delivered,
                isFromCurrentUser = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromInstrumentationTestCoverageReportGenerated
@Composable
private fun MessageBubbleSendingPreview() {
    MessengerTheme {
        MessageBubble(
            message = MessageUiModel(
                id = "3",
                text = "This message is currently being sent...",
                senderId = "current-user",
                senderName = "You",
                createdAt = "14:32",
                deliveryStatus = DeliveryStatus.Sending(progress = 75),
                isFromCurrentUser = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromInstrumentationTestCoverageReportGenerated
@Composable
private fun MessageBubbleFailedPreview() {
    MessengerTheme {
        Column {
            MessageBubble(
                message = MessageUiModel(
                    id = "4",
                    text = "This message failed to send.",
                    senderId = "current-user",
                    senderName = "You",
                    createdAt = "14:35",
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                    isFromCurrentUser = true,
                ),
            )
        }
    }
}
