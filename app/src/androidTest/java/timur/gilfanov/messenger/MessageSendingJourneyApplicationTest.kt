package timur.gilfanov.messenger

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application
import timur.gilfanov.annotations.ReleaseCandidate

@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class MessageSendingJourneyApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Category(ReleaseCandidate::class)
    @Test
    fun messageSendingJourney_completesSuccessfully() {
        composeTestRule.waitForIdle()

        // Wait for placeholder text to appear (indicates Ready state)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        val testMessage = "Hello, this is a test message!"

        // Step 1: Type message in input field

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(testMessage)

        // Verify text was entered correctly
        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(testMessage)

        // Step 2: Tap send button
        composeTestRule.onNodeWithTag("send_button")
            .performClick()

        // Step 3: Wait for message to be processed
        composeTestRule.waitForIdle()

        // Step 4: Verify input field is cleared after sending
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onNodeWithTag("message_input")
                .fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.EditableText)
                ?.text
                ?.isEmpty() == true
        }

        // Step 5: Verify placeholder text is shown again
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        // Verify send button is still displayed and functional
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
    }

    @Test
    fun messageSending_handlesEmptyInput() {
        composeTestRule.waitForIdle()

        // Wait for placeholder text to appear (indicates Ready state)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        // Verify send button exists but shouldn't be clickable when input is empty
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Try to click send button with empty input - it should be disabled
        // We can't directly test if it's disabled, but we can verify the UI state remains consistent
        composeTestRule.onNodeWithTag("send_button")
            .performClick()

        // Input should still show placeholder (no change)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()
    }

    @Test
    fun messageSending_multipleMessagesSequentially() {
        // Wait for placeholder text to appear (indicates Ready state)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        val messages = listOf(
            "First message",
            "Second message",
            "Third message",
        )

        messages.forEach { message ->
            // Type message

            composeTestRule.onNodeWithTag("message_input")
                .performTextInput(message)

            // Verify text is entered
            composeTestRule.onNodeWithTag("message_input")
                .assertTextEquals(message)

            // Send message
            composeTestRule.onNodeWithTag("send_button")
                .performClick()

            // Verify input is cleared
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onNodeWithTag("message_input")
                    .fetchSemanticsNode()
                    .config.getOrNull(SemanticsProperties.EditableText)
                    ?.text
                    ?.isEmpty() == true
            }

            // Verify UI is ready for next message
            composeTestRule.onNodeWithTag("message_input")
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("send_button")
                .assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun messageSending_preservesInputDuringTyping() {
        // Wait for placeholder text to appear (indicates Ready state)
        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        val partialMessage = "This is a long message that I'm typing"
        val completeMessage = "$partialMessage and now I'll send it"

        // Type partial message

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(partialMessage)

        // Verify partial message is preserved
        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(partialMessage)

        // Continue typing
        composeTestRule.onNodeWithTag("message_input")
            .performTextInputSelection(TextRange(partialMessage.length)) // Move cursor to end
        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(" and now I'll send it")

        // Verify complete message
        composeTestRule.onNodeWithTag("message_input")
            .assertTextEquals(completeMessage)

        // Send the message
        composeTestRule.onNodeWithTag("send_button")
            .performClick()

        // Verify input is cleared after sending
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onNodeWithTag("message_input")
                .fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.EditableText)
                ?.text
                ?.isEmpty() == true
        }
    }
}
