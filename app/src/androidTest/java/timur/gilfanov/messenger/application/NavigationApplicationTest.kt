package timur.gilfanov.messenger.application

import android.view.KeyEvent.KEYCODE_BACK
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import timur.gilfanov.messenger.data.repository.WithChatsParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class NavigationApplicationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object NavigationTestRepositoryModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = WithChatsParticipantRepository()
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun applicationTest_userCanNavigateFromChatListToChatScreen() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            // Debug: Check what chats are available
            onNodeWithText("Alice").assertIsDisplayed()
            onNodeWithText("Bob").assertIsDisplayed()

            // Verify Alice chat is displayed and click it
            waitUntilExactlyOneExists(hasTestTag("chat_item_550e8400-e29b-41d4-a716-446655440002"))
            onNodeWithTag("chat_item_550e8400-e29b-41d4-a716-446655440002").performClick()

            // Wait for chat screen to load and verify we're in Alice's chat
            waitUntilExactlyOneExists(hasTestTag("message_input"))
            onNodeWithText("Hello! 👋").assertIsDisplayed()

            // Verify input area is displayed
            onNodeWithTag("message_input").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
        }
    }

    @Test
    fun applicationTest_userCanNavigateBetweenMultipleChats() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            // Verify both chats are displayed in the list
            waitUntilExactlyOneExists(hasTestTag("chat_item_550e8400-e29b-41d4-a716-446655440002"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_550e8400-e29b-41d4-a716-446655440006"))

            // Click on Alice chat (first chat)
            onNodeWithTag("chat_item_550e8400-e29b-41d4-a716-446655440002").performClick()

            // Verify we're in Alice's chat
            waitUntilExactlyOneExists(hasTestTag("message_input"))
            onNodeWithText("Hello! 👋").assertIsDisplayed()

            // Navigate back to chat list
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KEYCODE_BACK)

            // Wait for chat list to reappear
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            // Click on Bob chat (second chat)
            waitUntilExactlyOneExists(hasTestTag("chat_item_550e8400-e29b-41d4-a716-446655440006"))
            onNodeWithTag("chat_item_550e8400-e29b-41d4-a716-446655440006").performClick()

            // Verify we're now in Bob's chat (different content)
            waitUntilExactlyOneExists(hasTestTag("message_input"))

            // Wait a bit for the chat to load properly
            waitForIdle()

            // Verify we can navigate to a different chat by ensuring Alice's message is gone
            // and the input area is still available (meaning we're in some chat screen)
            onNodeWithText("Hello! 👋").assertDoesNotExist()
            onNodeWithTag("message_input").assertIsDisplayed()
        }
    }
}
