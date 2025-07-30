package timur.gilfanov.messenger.feature.chatlist

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
import timur.gilfanov.messenger.data.repository.ALICE_CHAT_ID
import timur.gilfanov.messenger.data.repository.WithChatsParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatListNotEmptyFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object WithChatsRepositoryTestModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = WithChatsParticipantRepository()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserTestModule {
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
    fun chatListScreenWithChats_whenThereAreChatsEmptyStateNotShownInitially() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("search_button"))
        composeTestRule.onNodeWithTag("empty_state").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_list").assertExists()
    }

    @Test
    fun chatListScreenWithChats_handlesRotations() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            onNodeWithTag("search_button").assertExists()
            onNodeWithTag("new_chat_button").assertExists()
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            onNodeWithTag("search_button").assertExists()
            onNodeWithTag("new_chat_button").assertExists()
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
        }
    }

    // todo: @Test had started to hanging after compileSDK was updated from 35 to 36
    fun chatListScreen_handlesMultipleRotations() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            repeat(100) { index ->
                println("Rotation index: $index")
                composeTestRule.activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
                waitForIdle()

                waitUntilExactlyOneExists(hasTestTag("chat_list"))
                onNodeWithTag("chat_item_${ALICE_CHAT_ID}").assertExists()
            }
        }
    }
}
