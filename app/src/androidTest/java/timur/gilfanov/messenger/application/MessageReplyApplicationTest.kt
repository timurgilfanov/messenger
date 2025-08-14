package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.annotations.ApplicationTest
import timur.gilfanov.messenger.MainActivity
import timur.gilfanov.messenger.data.repository.InMemoryParticipantRepositoryFake
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class MessageReplyApplicationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        val testRepository = InMemoryParticipantRepositoryFake()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object MessageReplyTestRepositoryModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = testRepository
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun applicationTest_userReceivesMessageRepliesAndUnreadBadgeDisappears() {
        with(composeTestRule) {
            // Step 1: Wait for chat list to load and verify initial state (no unread messages)
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            // Get reference to the repository so we can simulate incoming messages
            val repository = testRepository
            val bobChatId = repository.bobChatId.id.toString()

            // Step 2: Verify Bob chat exists with no unread messages initially
            waitUntilExactlyOneExists(hasTestTag("chat_item_$bobChatId"))
            onNodeWithText("Bob").assertIsDisplayed()
            onNodeWithText("1").assertDoesNotExist()

            // Step 3: Simulate Bob sending a new message
            val incomingMessageText = "Hey! Are you available for a quick call? " +
                "I have something important to discuss."
            repository.simulateBobSendingMessage(incomingMessageText)

            // Step 4: Wait for UI to update and verify unread badge appears with partial message text
            waitUntilExactlyOneExists(hasText(incomingMessageText, substring = true))
            onNodeWithText("1").assertIsDisplayed()

            // Step 5: Tap on chat to read the message
            onNodeWithTag("chat_item_$bobChatId").performClick()

            // Step 6: Verify we're in the chat screen and can see the full message
            waitUntilExactlyOneExists(hasTextExactly(incomingMessageText))
            onNodeWithText(incomingMessageText).assertIsDisplayed()
            onNodeWithTag("message_input").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
            // todo: check received message read state in component or feature test

            // Step 7: Reply to the message
            val replyText = "Thanks! I'll get back to you soon."
            onNodeWithTag("message_input").performTextInput(replyText)
            onNodeWithText("Type a message...").assertIsNotDisplayed()
            onNodeWithTag("send_button").performClick()
            // todo: can use a robot to check input field is empty or not empty
            onNodeWithText("Type a message...").assertIsDisplayed()
            waitUntilExactlyOneExists(hasTextExactly(replyText))

            // Step 8: Navigate back to chat list
            activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            // Step 9: Verify chat list shows reply and no unread badge
            waitUntilExactlyOneExists(hasTestTag("chat_list"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_$bobChatId"))
            onNodeWithText("Bob").assertIsDisplayed()
            onNodeWithText(replyText, substring = true).assertIsDisplayed()
            onNodeWithText("1").assertDoesNotExist()
        }
    }
}
