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
class ChatScreenDisplayApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatScreen_displaysCorrectlyOnAppLaunch() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

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
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("loading_indicator"))
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
    }
}
