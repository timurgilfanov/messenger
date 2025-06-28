package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
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
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Message can not be longer then 2000 characters")
            .assertIsNotDisplayed()

        val tooLongMessage = "a".repeat(2001)

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(tooLongMessage)

        composeTestRule.waitForIdle()

        // Verify error message contains expected text about length limit
        composeTestRule.onNodeWithText("Message can not be longer then 2000 characters")
            .assertIsDisplayed()
    }

    @Test
    fun inputValidation_clearsErrorWhenTextBecomesValid() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        val tooLongMessage = "a".repeat(2001)
        composeTestRule.onNodeWithTag("message_input")
            .performTextInput(tooLongMessage)

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Message can not be longer then 2000 characters")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("message_input")
            .performTextReplacement("Valid message")

        composeTestRule.onNodeWithText("Message can not be longer then 2000 characters")
            .assertIsNotDisplayed()
    }

    @Test
    fun inputValidation_emptyInputDoesNotShowError() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("message_input"), timeoutMillis = 1000)

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput("Some text")

        composeTestRule.onNodeWithTag("message_input")
            .performTextClearance()

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()
    }
}
