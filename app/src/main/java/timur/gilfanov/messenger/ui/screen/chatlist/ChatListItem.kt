package timur.gilfanov.messenger.ui.screen.chatlist

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.ui.theme.MessengerTheme

private const val DAYS_IN_WEEK = 7
private const val DAYS_IN_YEAR = 365

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
        if (chatItem.lastMessageTime != null && chatItem.unreadCount == 0) {
            val context = LocalContext.current
            Text(
                text = formatTime(context, chatItem.lastMessageTime),
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

internal fun formatTime(context: Context, timestamp: Instant): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply {
        timeInMillis = timestamp.toEpochMilliseconds()
    }

    val daysDiff = getDaysDifference(now, messageTime)

    return when {
        daysDiff == 0 -> {
            // Today - show time
            SimpleDateFormat(
                "HH:mm",
                locale,
            ).format(Date(timestamp.toEpochMilliseconds()))
        }
        daysDiff == 1 -> {
            // Yesterday
            context.getString(R.string.yesterday)
        }
        daysDiff <= DAYS_IN_WEEK -> {
            // This week - show day of week
            SimpleDateFormat(
                "EEE",
                locale,
            ).format(Date(timestamp.toEpochMilliseconds()))
        }
        else -> {
            // Older than a week - show date
            SimpleDateFormat(
                "dd MMM",
                locale,
            ).format(Date(timestamp.toEpochMilliseconds()))
        }
    }
}

internal fun getDaysDifference(now: Calendar, messageTime: Calendar): Int {
    val nowDay = now.get(Calendar.DAY_OF_YEAR)
    val nowYear = now.get(Calendar.YEAR)
    val messageDay = messageTime.get(Calendar.DAY_OF_YEAR)
    val messageYear = messageTime.get(Calendar.YEAR)

    return if (nowYear == messageYear) {
        nowDay - messageDay
    } else {
        // Simple approximation for year difference
        (nowYear - messageYear) * DAYS_IN_YEAR + (nowDay - messageDay)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemTodayPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "John Doe (Today)",
                pictureUrl = null,
                lastMessage = "Hey there! How are you doing?",
                lastMessageTime = Clock.System.now(),
                unreadCount = 0,
                isOnline = true,
                lastOnlineTime = Clock.System.now(),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemYesterdayPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Alice (Yesterday)",
                pictureUrl = null,
                lastMessage = "The design looks great!",
                lastMessageTime = Clock.System.now() - 1.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemThisWeekPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Bob (This Week)",
                pictureUrl = null,
                lastMessage = "Meeting at 3 PM",
                lastMessageTime = Clock.System.now() - 3.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemOldDatePreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Sarah (Old Date)",
                pictureUrl = null,
                lastMessage = "Thanks for the help!",
                lastMessageTime = Clock.System.now() - 10.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListItemWithUnreadPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Mike (Unread)",
                pictureUrl = null,
                lastMessage = "Important message here",
                lastMessageTime = Clock.System.now() - 2.hours,
                unreadCount = 5,
                isOnline = true,
                lastOnlineTime = Clock.System.now(),
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
                name = "Emma (No Message)",
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

@Preview(showBackground = true, locale = "de")
@Composable
private fun ChatListItemGermanYesterdayPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Hans (German Yesterday)",
                pictureUrl = null,
                lastMessage = "Guten Tag!",
                lastMessageTime = Clock.System.now() - 1.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true, locale = "de")
@Composable
private fun ChatListItemGermanThisWeekPreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Klaus (German This Week)",
                pictureUrl = null,
                lastMessage = "Wie geht's?",
                lastMessageTime = Clock.System.now() - 3.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}

@Preview(showBackground = true, locale = "DE-de")
@Composable
private fun ChatListItemGermanOldDatePreview() {
    MessengerTheme {
        ChatListItem(
            chatItem = ChatListItemUiModel(
                id = ChatId(UUID.randomUUID()),
                name = "Petra (German Old Date)",
                pictureUrl = null,
                lastMessage = "Danke schön!",
                lastMessageTime = Clock.System.now() - 10.days,
                unreadCount = 0,
                isOnline = false,
                lastOnlineTime = null,
            ),
        )
    }
}
