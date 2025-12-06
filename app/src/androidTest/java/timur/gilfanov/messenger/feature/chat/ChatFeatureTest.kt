package timur.gilfanov.messenger.feature.chat

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.ChatScreenTestActivity
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.data.repository.DefaultIdentityRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.SettingsRepositoryStub

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    timur.gilfanov.messenger.di.TestUserModule::class,
    timur.gilfanov.messenger.di.TestChatModule::class,
)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object ChatScreenDisplayTestRepositoryModule {
        val repository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = repository

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = repository

        @Provides
        fun provideSettingsRepository(): SettingsRepository = SettingsRepositoryStub()

        @Provides
        fun provideIdentityRepository(): IdentityRepository = DefaultIdentityRepository()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserChatModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestUserId
        fun provideTestUserId(): String = AndroidTestDataHelper.USER_ID

        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestChatId
        fun provideTestChatId(): String = AndroidTestDataHelper.ALICE_CHAT_ID
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatScreen_inputAreaDisplaysCorrectly() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("message_input"),
            timeoutMillis = 5_000L,
        )

        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    // Stress test to amplify memory leaks using activity recreation
    @Test
    fun chatScreen_handlesMultipleRotations() = runTest {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            val testMessage = "Multi-rotation test"
            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                performTextInput(testMessage)
            }

            repeat(100) { index ->
                withContext(Dispatchers.Main) {
                    composeTestRule.activity.recreate()
                }

                waitUntilExactlyOneExists(
                    hasTestTag("message_input"),
                    timeoutMillis = 5_000L,
                )
                onNodeWithTag("message_input", useUnmergedTree = true).apply {
                    assertIsDisplayed()
                    assertTextEquals(testMessage)
                }

                onNodeWithTag("send_button")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun chatScreen_preservesInputTextOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )

            val testMessage = "This message should survive rotation"

            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                performTextInput(testMessage)
                assertTextEquals(testMessage)
            }

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("message_input").apply {
                assertIsDisplayed()
                assertTextEquals(testMessage)
            }

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            waitForIdle()

            onNodeWithTag("message_input")
                .assertTextEquals(testMessage)
        }
    }

    @Test
    fun chatScreen_preservesUIStateOnRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()

            onNodeWithText("Type a message...")
                .assertIsDisplayed()

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            waitForIdle()

            onNodeWithTag("message_input")
                .assertIsDisplayed()

            onNodeWithTag("send_button")
                .assertIsDisplayed()
        }
    }

    @Test
    fun inputValidation_showsErrorForTooLongMessage() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
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
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()
            repeat(100) {
                onNodeWithTag("message_input").performTextReplacement(tooLongMessage)
                waitUntilExactlyOneExists(hasText(errorText))
                onNodeWithTag("message_input").performTextReplacement("Valid message")
                waitUntilDoesNotExist(hasText(errorText))
            }
        }
    }

    @Test
    fun inputValidation_emptyInputDoesNotShowError() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("message_input").performTextInput("Some text")
            onNodeWithTag("message_input").performTextClearance()
            waitForIdle()
            onNodeWithText("Type a message...").assertIsDisplayed()
        }
    }
}
