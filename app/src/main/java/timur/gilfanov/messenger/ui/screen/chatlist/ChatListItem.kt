package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.datetime.Clock
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.ui.theme.MessengerTheme

private const val TIME_DISPLAY_LENGTH = 5

@Composable
fun ChatListItem(
    chatItem: ChatListItemUiModel,
    modifier: Modifier = Modifier,
    onClick: (ChatId) -> Unit = {},
    onDelete: (ChatId) -> Unit = {},
) {
    SwipeToActionRow(
        modifier = modifier,
        onStartAction = { onDelete(chatItem.id) },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(chatItem.id) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatAvatar(
                    pictureUrl = chatItem.pictureUrl,
                    name = chatItem.name,
                    isOnline = chatItem.isOnline,
                )
                ChatInfo(chatItem)
                EndSideInfo(chatItem)
            }
        }
    }
}

@Composable
private fun EndSideInfo(chatItem: ChatListItemUiModel) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (chatItem.lastMessageTime != null) {
            Text(
                text = formatTime(chatItem.lastMessageTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        if (chatItem.unreadCount > 0) {
            UnreadBadge(count = chatItem.unreadCount)
        }
    }
}

@Composable
private fun RowScope.ChatInfo(chatItem: ChatListItemUiModel) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = chatItem.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (chatItem.lastMessage != null) {
            Text(
                text = chatItem.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatAvatar(pictureUrl: String?, name: String, isOnline: Boolean) {
    BadgedBox(
        badge = {
            if (isOnline) {
                OnlineIndicator()
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (pictureUrl != null) {
                // TODO AsyncImage will be added when image loading is implemented
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                // Show first letter of name or group icon
                if (name.isNotEmpty()) {
                    Text(
                        text = name.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Group",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineIndicator() {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary), // todo some green color
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Badge(
        containerColor = MaterialTheme.colorScheme.error, // todo some blue color
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
        )
    }
}

private fun formatTime(timestamp: String): String {
    // Simple time formatting - shows first 5 characters (HH:MM)
    // TODO Proper time formatting will be implemented later
    return timestamp.take(TIME_DISPLAY_LENGTH)
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "John Doe",
                pictureUrl = null,
                lastMessage = "Hey there! How are you doing?",
                lastMessageTime = "14:30",
                unreadCount = 3,
                isOnline = true,
                lastOnlineTime = Clock.System.now(),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemGroupPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Project Team",
                pictureUrl = null,
                lastMessage = "Alice: The design looks great!",
                lastMessageTime = "Yesterday",
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemNoMessagePreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Sarah Wilson",
                pictureUrl = null,
                lastMessage = null,
                lastMessageTime = null,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}
