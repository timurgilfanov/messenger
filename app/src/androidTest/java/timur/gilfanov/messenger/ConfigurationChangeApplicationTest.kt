package timur.gilfanov.messenger

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.messenger.Application
import timur.gilfanov.messenger.ReleaseCandidate

@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ConfigurationChangeApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatScreen_preservesInputTextOnRotation() {
        composeTestRule.waitForIdle()

        val testMessage = "This message should survive rotation"

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
        composeTestRule.waitForIdle()

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
        composeTestRule.waitForIdle()

        val testMessage = "Multi-rotation test"

        // Enter text
        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(testMessage)

        // Perform multiple rotations
        val orientations = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        )

        orientations.forEach { orientation ->
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
