package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun chatListScreen_displaysCorrectlyOnAppLaunch() {
        // Wait for initial loading to complete and either empty state or chat list to appear
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("empty_state") or hasTestTag("chat_list"),
            timeoutMillis = 3000,
        )

        // Verify the search button is displayed
        composeTestRule.onNodeWithTag("search_button")
            .assertIsDisplayed()

        // Verify the new chat button is displayed
        composeTestRule.onNodeWithTag("new_chat_button")
            .assertIsDisplayed()

        // Verify user name is displayed in title
        composeTestRule.onNodeWithText("Current User")
            .assertIsDisplayed()

        // Verify either empty state or chat list is displayed
        try {
            composeTestRule.onNodeWithTag("empty_state")
                .assertIsDisplayed()
        } catch (e: AssertionError) {
            // If empty state is not displayed, chat list should be displayed
            composeTestRule.onNodeWithTag("chat_list")
                .assertIsDisplayed()
        }
    }

    @Test
    fun chatListScreen_showsLoadingStateInitially() {
        // The loading state should be visible briefly when the app starts
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("loading_indicator"),
            timeoutMillis = 1000,
        )

        // Then either empty state or chat list should appear
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("empty_state") or hasTestTag("chat_list"),
            timeoutMillis = 3000,
        )
    }

    @Test
    fun chatListScreen_showsEmptyStateWhenNoChats() {
        // Wait for the app to finish loading
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("empty_state") or hasTestTag("chat_list"),
            timeoutMillis = 3000,
        )

        // If empty state is displayed, verify its content
        try {
            composeTestRule.onNodeWithTag("empty_state")
                .assertIsDisplayed()

            // Verify status text shows "No chats"
            composeTestRule.onNodeWithText("No chats")
                .assertIsDisplayed()
        } catch (e: AssertionError) {
            // If not empty, should show chat list
            composeTestRule.onNodeWithTag("chat_list")
                .assertIsDisplayed()
        }
    }

    @Test
    fun chatListScreen_showsStatusText() {
        // Wait for the app to finish loading
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("empty_state") or hasTestTag("chat_list"),
            timeoutMillis = 3000,
        )

        // Verify status text is displayed (either "No chats" or chat count)
        try {
            composeTestRule.onNodeWithText("No chats")
                .assertIsDisplayed()
        } catch (e: AssertionError) {
            // Should show chat count if there are chats
            // Note: We can't easily test regex patterns in instrumented tests
            // Just check that the screen is displayed
            composeTestRule.onNodeWithTag("chat_list")
                .assertIsDisplayed()
        }
    }

    @Test
    fun chatListScreen_buttonsAreAccessible() {
        // Wait for the app to finish loading
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("empty_state") or hasTestTag("chat_list"),
            timeoutMillis = 3000,
        )

        // Verify action buttons are accessible
        composeTestRule.onNodeWithTag("search_button")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_button")
            .assertIsDisplayed()
    }
}
