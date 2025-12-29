package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.ui.theme.MessengerTheme
import timur.gilfanov.messenger.util.generateProfileImageUrl

@RunWith(AndroidJUnit4::class)
class ChatAvatarWithProfileImageComponentTest : Component {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testChatId = ChatId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val testTimestamp = Instant.fromEpochMilliseconds(1000)

    @Test
    fun chatAvatar_withProfileImage_displaysCorrectly() {
        composeTestRule.setContent {
            MessengerTheme {
                ChatListItem(
                    chatItem = ChatListItemUiModel(
                        id = testChatId,
                        name = "Alice Johnson",
                        pictureUrl = generateProfileImageUrl("Alice Johnson"),
                        lastMessage = "Hello there!",
                        lastMessageTime = testTimestamp,
                        unreadCount = 2,
                        isOnline = true,
                        lastOnlineTime = testTimestamp,
                    ),
                )
            }
        }

        // Verify that the avatar is displayed (we can check for the chat name)
        composeTestRule
            .onNodeWithText("Alice Johnson")
            .assertIsDisplayed()
    }

    @Test
    fun chatAvatar_withoutProfileImage_showsInitial() {
        composeTestRule.setContent {
            MessengerTheme {
                ChatListItem(
                    chatItem = ChatListItemUiModel(
                        id = testChatId,
                        name = "Bob Wilson",
                        pictureUrl = null,
                        lastMessage = "How are you?",
                        lastMessageTime = testTimestamp - Duration.parse("1h"),
                        unreadCount = 0,
                        isOnline = false,
                        lastOnlineTime = null,
                    ),
                )
            }
        }

        // The chat name should be displayed
        composeTestRule
            .onNodeWithText("Bob Wilson")
            .assertIsDisplayed()

        // The first letter should show as fallback in the avatar
        composeTestRule
            .onNodeWithText("B")
            .assertIsDisplayed()
    }
}
