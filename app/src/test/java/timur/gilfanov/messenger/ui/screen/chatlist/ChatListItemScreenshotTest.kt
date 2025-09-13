package timur.gilfanov.messenger.ui.screen.chatlist

import java.util.UUID
import kotlin.time.Instant
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

class ChatListItemScreenshotTest : ScreenshotTestBase() {

    // Fixed timestamp: 2022-01-01 00:00:00 UTC
    val timestamp = Instant.fromEpochMilliseconds(1640995200000)
    private val baseChatItem = ChatListItemUiModel(
        id = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        name = "John Doe",
        pictureUrl = null,
        lastMessage = "Hello there!",
        lastMessageTime = timestamp,
        unreadCount = 0,
        isOnline = true,
        lastOnlineTime = timestamp,
    )

    @Test
    fun shortNameShortMessage_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(chatItem = baseChatItem)
        }
    }

    @Test
    fun shortNameShortMessage_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(chatItem = baseChatItem)
        }
    }

    @Test
    fun longNameLongMessage_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "Dr. Alexandra Catherine Richardson-Williams",
                    lastMessage = "This is a very long message that should wrap and be truncated " +
                        "with ellipsis to test how the UI handles long content gracefully",
                ),
            )
        }
    }

    @Test
    fun longNameLongMessage_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "Dr. Alexandra Catherine Richardson-Williams",
                    lastMessage = "This is a very long message that should wrap and be truncated " +
                        "with ellipsis to test how the UI handles long content gracefully",
                ),
            )
        }
    }

    @Test
    fun withUnreadBadgeSmall_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    unreadCount = 3,
                    lastMessage = "Important unread message",
                ),
            )
        }
    }

    @Test
    fun withUnreadBadgeSmall_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    unreadCount = 3,
                    lastMessage = "Important unread message",
                ),
            )
        }
    }

    @Test
    fun withUnreadBadgeLarge_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    unreadCount = 999,
                    lastMessage = "Many unread messages",
                ),
            )
        }
    }

    @Test
    fun withUnreadBadgeLarge_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    unreadCount = 999,
                    lastMessage = "Many unread messages",
                ),
            )
        }
    }

    @Test
    fun offline_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    isOnline = false,
                    lastMessage = "User is offline",
                ),
            )
        }
    }

    @Test
    fun offline_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    isOnline = false,
                    lastMessage = "User is offline",
                ),
            )
        }
    }

    @Test
    fun noLastMessage_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    lastMessage = null,
                    lastMessageTime = null,
                ),
            )
        }
    }

    @Test
    fun noLastMessage_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    lastMessage = null,
                    lastMessageTime = null,
                ),
            )
        }
    }

    @Test
    fun emptyName_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "",
                    lastMessage = "User with no name",
                ),
            )
        }
    }

    @Test
    fun emptyName_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "",
                    lastMessage = "User with no name",
                ),
            )
        }
    }

    @Test
    fun singleCharacterName_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "A",
                    lastMessage = "Single letter name",
                ),
            )
        }
    }

    @Test
    fun singleCharacterName_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "A",
                    lastMessage = "Single letter name",
                ),
            )
        }
    }

    @Test
    fun smallScreen_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.LIGHT,
            ),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "Long Name That Might Wrap",
                    lastMessage = "Message that should fit on small screen",
                ),
            )
        }
    }

    @Test
    fun largeScreen_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.DARK,
            ),
        ) {
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "User On Large Screen",
                    lastMessage = "This message has more space to display on larger screens",
                ),
            )
        }
    }

    @Test
    fun rtl_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "المستخدم العربي",
                    lastMessage = "رسالة باللغة العربية للاختبار",
                    unreadCount = 5,
                ),
            )
        }
    }

    @Test
    fun rtl_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            ChatListItem(
                chatItem = baseChatItem.copy(
                    name = "المستخدم العربي",
                    lastMessage = "رسالة باللغة العربية للاختبار",
                    unreadCount = 5,
                ),
            )
        }
    }
}
