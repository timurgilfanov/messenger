package timur.gilfanov.messenger.application

import android.view.KeyEvent.KEYCODE_BACK
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
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
import timur.gilfanov.messenger.data.repository.ALICE_CHAT_ID
import timur.gilfanov.messenger.data.repository.ALICE_TEXT_1
import timur.gilfanov.messenger.data.repository.BOB_CHAT_ID
import timur.gilfanov.messenger.data.repository.BOB_TEXT_1
import timur.gilfanov.messenger.data.repository.MessengerNotEmptyRepositoryFake
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository

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
        fun provideChatRepository(): ChatRepository = MessengerNotEmptyRepositoryFake()

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = MessengerNotEmptyRepositoryFake()
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun applicationTest_userCanNavigateFromChatListToChatScreen() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            onNodeWithText("Alice").assertIsDisplayed()
            onNodeWithText("Bob").assertIsDisplayed()
            onNodeWithTag("chat_item_${ALICE_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(ALICE_TEXT_1))
            onNodeWithText(ALICE_TEXT_1).assertIsDisplayed()

            onNodeWithTag("message_input").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
        }
    }

    @Test
    fun applicationTest_userCanNavigateBetweenMultipleChats() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_${BOB_CHAT_ID}"))

            onNodeWithTag("chat_item_${ALICE_CHAT_ID}").performClick()
            waitUntilExactlyOneExists(hasTextExactly(ALICE_TEXT_1))
            onNodeWithText(ALICE_TEXT_1).assertIsDisplayed()

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KEYCODE_BACK)

            waitUntilExactlyOneExists(hasTestTag("chat_item_${BOB_CHAT_ID}"))
            onNodeWithTag("chat_item_${BOB_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(BOB_TEXT_1))
            onNodeWithText(BOB_TEXT_1).assertIsDisplayed()
        }
    }
}
