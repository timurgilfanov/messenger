package timur.gilfanov.messenger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.messenger.data.repository.InMemoryParticipantRepository

@HiltAndroidTest
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatScreenInstrumentationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var participantRepository: InMemoryParticipantRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun chatScreen_sendMessageAndStatePersists() {
        // Verify initial messages
        composeRule.onNodeWithText("Hello! 👋").assertIsDisplayed()
        composeRule.onNodeWithText("How are you doing today?").assertIsDisplayed()

        // Send new message
        val message = "Test instrumentation message"
        composeRule.onNodeWithTag("message_input").performTextInput(message)
        composeRule.onNodeWithTag("send_button").performClick()

        // Check that the new message is displayed with status
        composeRule.onNodeWithText(message).assertIsDisplayed()
        val hasDelivered =
            composeRule.onAllNodes(hasContentDescription("Delivered"))
                .fetchSemanticsNodes().isNotEmpty()
        val hasRead =
            composeRule.onAllNodes(hasContentDescription("Read"))
                .fetchSemanticsNodes().isNotEmpty()
        assertTrue(hasDelivered || hasRead)

        // Recreate activity and verify state persists
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Hello! 👋").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }
}
