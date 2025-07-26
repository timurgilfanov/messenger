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
import timur.gilfanov.annotations.ReleaseCandidateTest
import timur.gilfanov.messenger.data.repository.InMemoryParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class MessageSendingApplicationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object MessageSendingTestRepositoryModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = InMemoryParticipantRepository()
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @ReleaseCandidateTest
    @Test
    fun messageSending_completesSuccessfully() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("message_input"))
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
            waitUntilExactlyOneExists(hasTestTag("message_input"))
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
            waitUntilExactlyOneExists(hasTestTag("message_input"))
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

    /* todo: this test is failed on application-test-devices, needs investigation

MessageSendingApplicationTest.messageSending_preservesInputDuringTyping: app/src/androidTest/java/timur/gilfanov/messenger/MessageSendingApplicationTest.kt#L116
androidx.compose.ui.test.junit4.android.ComposeNotIdleException: Idling resource timed out: possibly due to compose being busy.
IdlingResourceRegistry has the following idling resources registered:

     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun messageSending_preservesInputDuringTyping() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("message_input"))
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
