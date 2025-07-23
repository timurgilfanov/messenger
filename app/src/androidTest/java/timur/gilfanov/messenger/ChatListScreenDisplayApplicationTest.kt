package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
    fun chatListScreen_showsEmptyStateInitiallyThenChatList() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("empty_state"),
        )

        composeTestRule.onNodeWithTag("search_button")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_button")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("start_first_chat_button")
            .assertIsDisplayed()
            .assertIsEnabled()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("chat_list"))

        composeTestRule.onNodeWithTag("search_button")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_button")
            .assertIsDisplayed()
    }
}
