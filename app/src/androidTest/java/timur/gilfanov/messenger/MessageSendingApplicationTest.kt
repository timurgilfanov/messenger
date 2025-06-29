package timur.gilfanov.messenger

import androidx.compose.ui.semantics.SemanticsProperties.Disabled
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
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

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class MessageSendingApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Category(ReleaseCandidate::class)
    @Test
    fun messageSending_completesSuccessfully() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1_000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            val testMessage = "Hello, this is a test message!"
            onNodeWithTag("message_input").performTextInput(testMessage)
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(testMessage)
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            waitUntil(timeoutMillis = 1_000) {
                onNodeWithTag("message_input")
                    .fetchSemanticsNode()
                    .config.getOrNull(EditableText)
                    ?.text
                    ?.isEmpty() == true
            }
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
        }
    }

    @Test
    fun messageSending_handlesEmptyInput() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1_000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            onNodeWithText("Type a message...").assertIsDisplayed()
        }
    }

    @Test
    fun messageSending_multipleMessagesSequentially() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1_000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
            repeat(100) {
                val message = "Test message #$it"
                onNodeWithTag("message_input").run {
                    performTextInput(message)
                    waitUntil(timeoutMillis = 1_000) {
                        fetchSemanticsNode().config[EditableText].text == message
                    }
                }
                onNodeWithText("Type a message...").assertIsNotDisplayed()
                onNodeWithTag("send_button").let {
                    it.assertIsEnabled()
                    it.performClick()
                }
                waitUntil(timeoutMillis = 1_000) {
                    onNodeWithTag("message_input")
                        .fetchSemanticsNode()
                        .config.run {
                            getOrNull(EditableText)?.text?.isEmpty() == true &&
                                getOrNull(Disabled) == null
                        }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun messageSending_preservesInputDuringTyping() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1_000)
            onNodeWithText("Type a message...").assertIsDisplayed()

            val partialMessage = "This is a long message that I'm typing"
            val completeMessage = "$partialMessage and now I'll send it"

            onNodeWithTag("message_input").performTextInput(partialMessage)
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(partialMessage)
            onNodeWithTag("message_input")
                .performTextInputSelection(TextRange(partialMessage.length))
            onNodeWithTag("message_input").performTextInput(" and now I'll send it")
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(completeMessage)
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            waitUntil(timeoutMillis = 1_000) {
                onNodeWithTag("message_input")
                    .fetchSemanticsNode()
                    .config.getOrNull(EditableText)
                    ?.text
                    ?.isEmpty() == true
            }
        }
    }
}
