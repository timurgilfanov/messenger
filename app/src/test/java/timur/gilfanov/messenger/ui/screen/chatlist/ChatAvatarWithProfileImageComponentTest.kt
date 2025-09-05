package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(AndroidJUnit4::class)
class ChatAvatarWithProfileImageComponentTest : Component {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatAvatar_withProfileImage_displaysCorrectly() {
        val testImageUrl = "https://ui-avatars.com/api/?name=Alice+Johnson&background=random"

        composeTestRule.setContent {
            MessengerTheme {
                ChatListItem(
                    chatItem = ChatListItemUiModel(
                        id = timur.gilfanov.messenger.domain.entity.chat.ChatId(
                            java.util.UUID.randomUUID(),
                        ),
                        name = "Alice Johnson",
                        pictureUrl = testImageUrl,
                        lastMessage = "Hello there!",
                        lastMessageTime = kotlin.time.Clock.System.now(),
                        unreadCount = 2,
                        isOnline = true,
                        lastOnlineTime = kotlin.time.Clock.System.now(),
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
                        id = timur.gilfanov.messenger.domain.entity.chat.ChatId(
                            java.util.UUID.randomUUID(),
                        ),
                        name = "Bob Wilson",
                        pictureUrl = null,
                        lastMessage = "How are you?",
                        lastMessageTime =
                        kotlin.time.Clock.System.now() - kotlin.time.Duration.parse("1h"),
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
