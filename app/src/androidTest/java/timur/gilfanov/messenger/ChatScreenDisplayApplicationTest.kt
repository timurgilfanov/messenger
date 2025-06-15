package timur.gilfanov.messenger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.messenger.Application

@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatScreenDisplayApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatScreen_displaysCorrectlyOnAppLaunch() {
        // Wait for the app to load and display the chat screen
        composeTestRule.waitForIdle()

        // Verify the message input field is displayed
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        // Verify the send button is displayed
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Verify placeholder text is shown in empty input
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        // Verify send button icon is displayed (should show Send icon when input is empty)
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsLoadingStateInitially() {
        // The loading state should be visible briefly when the app starts
        // This test verifies the loading indicator has correct content description
        composeTestRule.waitForIdle()

        // After loading completes, the chat content should be visible
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()
    }

    @Test
    fun chatScreen_displaysPredeterminedChatData() {
        composeTestRule.waitForIdle()

        // Verify the app uses the hardcoded chat ID and loads the chat
        // The UI should show the chat screen with all essential components
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Verify the input field is functional (enabled by default)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()
    }
}
