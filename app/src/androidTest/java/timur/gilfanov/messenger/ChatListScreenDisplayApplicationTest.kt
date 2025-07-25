package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatListScreenDisplayApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Test
    fun chatListScreen_whenThereAreChatsEmptyStateNotShownInitially() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("search_button"))
        composeTestRule.onNodeWithTag("empty_state").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_list").assertExists()
    }

    @Test
    fun chatListScreen_whenThereAreNoChatsEmptyStateIsShownInitially() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("search_button"))
        composeTestRule.onNodeWithTag("empty_state").assertExists()
        composeTestRule.onNodeWithTag("chat_list").assertDoesNotExist()
    }
}
