package timur.gilfanov.messenger.feature.chatlist

import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.annotations.FeatureTest
import timur.gilfanov.messenger.ChatListScreenTestActivity
import timur.gilfanov.messenger.data.repository.ALICE_CHAT_ID
import timur.gilfanov.messenger.data.repository.USER_ID
import timur.gilfanov.messenger.data.repository.WithChatsParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatListNotEmptyFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var activityScenario: ActivityScenario<ChatListScreenTestActivity>

    @Module
    @InstallIn(SingletonComponent::class)
    object WithChatsRepositoryTestModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = WithChatsParticipantRepository()
    }

    @Before
    fun setup() {
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ChatListScreenTestActivity::class.java).apply {
            putExtra(ChatListScreenTestActivity.EXTRA_CURRENT_USER_ID, USER_ID)
        }

        activityScenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        activityScenario.close()
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

            activityScenario.onActivity { activity ->
                activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            }
            waitForIdle()

            onNodeWithTag("search_button").assertExists()
            onNodeWithTag("new_chat_button").assertExists()
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
        }
    }

    @Test
    fun chatListScreen_handlesMultipleRotations() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            repeat(100) { index ->
                activityScenario.onActivity { activity ->
                    activity.requestedOrientation = if (index % 2 == 0) {
                        SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                waitForIdle()

                onNodeWithTag("chat_list").assertExists()
                onNodeWithTag("chat_item_${ALICE_CHAT_ID}").assertExists()
            }
        }
    }
}
