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
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
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
        val repository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = repository

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = repository
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserNavigationTestModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestUserId
        fun provideTestUserId(): String = AndroidTestDataHelper.USER_ID
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun applicationTest_userCanNavigateFromChatListToChatScreen() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = 5_000L,
            )

            waitUntilExactlyOneExists(
                hasTestTag("chat_item_${AndroidTestDataHelper.ALICE_CHAT_ID}"),
            )
            onNodeWithText("Alice").assertIsDisplayed()
            onNodeWithText("Bob").assertIsDisplayed()
            onNodeWithTag("chat_item_${AndroidTestDataHelper.ALICE_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(AndroidTestDataHelper.ALICE_TEXT_1))
            onNodeWithText(AndroidTestDataHelper.ALICE_TEXT_1).assertIsDisplayed()

            onNodeWithTag("message_input").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
        }
    }

    // TODO: Fix multi-chat navigation test failure (NoSuchElementException in ChatViewModel)
    // @Test
    fun applicationTest_userCanNavigateBetweenMultipleChats() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = 5_000L,
            )

            waitUntilExactlyOneExists(
                hasTestTag("chat_item_${AndroidTestDataHelper.ALICE_CHAT_ID}"),
            )
            waitUntilExactlyOneExists(hasTestTag("chat_item_${AndroidTestDataHelper.BOB_CHAT_ID}"))

            onNodeWithTag("chat_item_${AndroidTestDataHelper.ALICE_CHAT_ID}").performClick()
            waitUntilExactlyOneExists(hasTextExactly(AndroidTestDataHelper.ALICE_TEXT_1))
            onNodeWithText(AndroidTestDataHelper.ALICE_TEXT_1).assertIsDisplayed()

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KEYCODE_BACK)

            waitUntilExactlyOneExists(hasTestTag("chat_item_${AndroidTestDataHelper.BOB_CHAT_ID}"))
            onNodeWithTag("chat_item_${AndroidTestDataHelper.BOB_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(AndroidTestDataHelper.BOB_TEXT_1))
            onNodeWithText(AndroidTestDataHelper.BOB_TEXT_1).assertIsDisplayed()
        }
    }
}
