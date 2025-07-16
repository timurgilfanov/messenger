package timur.gilfanov.messenger

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application
import timur.gilfanov.annotations.ReleaseCandidate

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatScreenConfigurationChangeApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Test
    fun chatScreen_preservesInputTextOnRotation() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

        val testMessage = "This message should survive rotation"

        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        // Type text in the message input
        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(testMessage)

        // Verify text was entered
        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(testMessage)

        // Rotate to landscape
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()

        // Verify text is preserved after rotation
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(testMessage)

        // Rotate back to portrait
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()

        // Verify text is still preserved
        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(testMessage)
    }

    @Test
    fun chatScreen_preservesUIStateOnRotation() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

        // Verify initial UI state
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        // Rotate to landscape
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()

        // Verify UI elements are still displayed after rotation
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Rotate back to portrait
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()

        // Verify UI elements are still displayed
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
    }

    @Category(ReleaseCandidate::class)
    @Test
    fun chatScreen_handlesMultipleRotations() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
        val testMessage = "Multi-rotation test"
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(testMessage)

        repeat(20) { index ->
            val orientation = if (index % 2 == 0) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeTestRule.activity.requestedOrientation = orientation
            composeTestRule.waitForIdle()

            // Verify the app remains functional after each rotation
            composeTestRule.onNodeWithTag("message_input")
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("send_button")
                .assertIsDisplayed()

            // Verify text is preserved
            composeTestRule.onNodeWithTag("message_input")
                .assertTextEquals(testMessage)
        }
    }
}
