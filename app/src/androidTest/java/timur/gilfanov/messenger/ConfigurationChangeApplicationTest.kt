package timur.gilfanov.messenger

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
class ConfigurationChangeApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatScreen_preservesInputTextOnRotation() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

            val testMessage = "This message should survive rotation"

            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                performTextInput(testMessage)
                assertTextEquals(testMessage)
            }

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                assertTextEquals(testMessage)
            }

            activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            waitForIdle()

            onNodeWithTag("message_input")
                .assertTextEquals(testMessage)
        }
    }

    @Test
    fun chatScreen_preservesUIStateOnRotation() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()

            onNodeWithText("Type a message...")
                .assertIsDisplayed()

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()

            activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            waitForIdle()

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()
        }
    }

    @Category(ReleaseCandidate::class)
    @Test
    fun chatScreen_handlesMultipleRotations() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
            val testMessage = "Multi-rotation test"
            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                performTextInput(testMessage)
            }

            repeat(100) { index ->
                activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
                waitForIdle()

                onNodeWithTag("message_input", useUnmergedTree = true).apply {
                    assertIsDisplayed()
                    assertTextEquals(testMessage)
                }

                onNodeWithTag("send_button")
                    .assertIsDisplayed()
            }
        }
    }
}
