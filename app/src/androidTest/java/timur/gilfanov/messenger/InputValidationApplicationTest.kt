package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class InputValidationApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun inputValidation_showsErrorForTooLongMessage() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithText("Message can not be longer then 2000 characters").assertIsNotDisplayed()
            val tooLongMessage = "a".repeat(2001)
            onNodeWithTag("message_input").performTextInput(tooLongMessage)
            waitForIdle()
            onNodeWithText("Message can not be longer then 2000 characters").assertIsDisplayed()
        }
    }

    @Test
    fun inputValidation_clearsErrorWhenTextBecomesValid() {
        val tooLongMessage = "a".repeat(2001)
        val errorText = "Message can not be longer then 2000 characters"
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            repeat(100) {
                println("Iteration: $it")
                onNodeWithTag("message_input").performTextReplacement(tooLongMessage)
                waitUntilAtLeastOneExists(hasText(errorText), timeoutMillis = 1_000)
                onNodeWithTag("message_input").performTextReplacement("Valid message")
                waitUntilDoesNotExist(hasText(errorText), timeoutMillis = 1_000)
            }
        }
    }

    @Test
    fun inputValidation_emptyInputDoesNotShowError() {
        with(composeTestRule) {
            waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("message_input").performTextInput("Some text")
            onNodeWithTag("message_input").performTextClearance()
            waitForIdle()
            onNodeWithText("Type a message...").assertIsDisplayed()
        }
    }
}
