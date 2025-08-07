package timur.gilfanov.messenger.feature.chatlist

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import timur.gilfanov.annotations.FeatureTest
import timur.gilfanov.messenger.ChatListScreenTestActivity
import timur.gilfanov.messenger.data.repository.MessengerEmptyRepositoryStub
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, timur.gilfanov.messenger.di.TestUserModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatListEmptyFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object EmptyRepositoryTestModule {
        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = MessengerEmptyRepositoryStub()

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = MessengerEmptyRepositoryStub()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserEmptyTestModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestUserId
        fun provideTestUserId(): String = timur.gilfanov.messenger.data.repository.USER_ID
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatListScreenEmpty_whenThereAreNoChatsEmptyStateIsShownInitially() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("search_button"))
        composeTestRule.onNodeWithTag("empty_state").assertExists()
        composeTestRule.onNodeWithTag("chat_list").assertDoesNotExist()
    }

    @Test
    fun chatListScreenEmpty_handlesRotations() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("empty_state"))

            onNodeWithTag("search_button").assertExists()
            onNodeWithTag("new_chat_button").assertExists()

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("search_button").assertExists()
            onNodeWithTag("new_chat_button").assertExists()
            onNodeWithTag("empty_state").assertExists()
        }
    }
}
