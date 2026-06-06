package timur.gilfanov.messenger.feature.chat

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.ChatScreenTestActivity
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.auth.di.AuthDataModule
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestChatModule
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.LONG_CONVERSATION
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsHelper
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.util.Logger

/**
 * Verifies acceptance criterion: scroll position survives configuration changes for the loaded
 * message window. Opens a long conversation, scrolls up into older history away from the live edge,
 * rotates the device, and asserts the list stays parked in older history (the newest message is
 * not shown) rather than snapping back to the bottom.
 *
 * The assertion is intentionally coarse — "did not reset to the live edge" — not "restored the
 * exact same item". Scroll preservation is backed by
 * [androidx.compose.foundation.lazy.rememberLazyListState] (a numeric index saved across
 * recreation) plus the ViewModel's `pagedMessages.cachedIn(viewModelScope)` (loaded pages kept
 * alive while the retained ViewModel survives the configuration change). Because Paging
 * re-presents its loaded window on recreation, the restored offset drifts by a few items, so an
 * exact-item assertion would be both flaky and stricter than the criterion ("where the same
 * message window remains available") actually requires.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    TestChatModule::class,
    AuthDataModule::class,
)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatScrollPreservationFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object LongConversationRepositoryModule {
        val repository = AndroidTestRepositoryWithRealImplementation(LONG_CONVERSATION)

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = repository

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = repository

        @Provides
        fun provideSettingsRepository(): SettingsRepository = SettingsRepositoryStub()

        @Provides
        @Singleton
        fun provideAuthRepository(): AuthRepository =
            AuthRepositoryFake(AndroidTestSettingsHelper.testSession)

        @Provides
        @Singleton
        fun provideRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob())

        @Provides
        @Singleton
        fun provideLocaleRepository(logger: Logger): LocaleRepository = LocaleRepositoryImpl(logger)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserChatModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestChatId
        fun provideTestChatId(): String = AndroidTestDataHelper.LONG_CHAT_ID
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatScreen_preservesScrollPositionOnRotation() {
        with(composeTestRule) {
            val newestText = AndroidTestDataHelper.longConversationMessageText(
                AndroidTestDataHelper.LONG_CONVERSATION_SIZE,
            )
            val oldestText = AndroidTestDataHelper.longConversationMessageText(1)

            waitUntilExactlyOneExists(hasTestTag("message_list"), timeoutMillis = 5_000L)

            // Opens at the live edge: the newest message is visible at the bottom.
            waitUntilExactlyOneExists(hasText(newestText), timeoutMillis = 5_000L)
            onNodeWithText(newestText).assertIsDisplayed()

            // Scroll up into older history, away from the live edge (drives paging appends across
            // page boundaries). The newest message is no longer composed once scrolled past.
            onNodeWithTag("message_list").performScrollToNode(hasText(oldestText))
            waitUntilExactlyOneExists(hasText(oldestText), timeoutMillis = 5_000L)
            onNodeWithText(newestText).assertDoesNotExist()

            // Rotate. Capture the Activity before the config change and wait for it to be destroyed
            // so subsequent queries resolve against the post-rotation composition, not the stale one.
            val oldActivity = activity
            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitUntil(timeoutMillis = 5_000L) { oldActivity.isDestroyed }
            waitUntilExactlyOneExists(hasTestTag("message_list"), timeoutMillis = 10_000L)

            // Scroll position survived recreation: the list is still parked in older history and
            // did NOT snap back to the live edge. We assert the coarse, deterministic invariant
            // (newest absent, older content present) rather than an exact restored message, because
            // Paging re-presents its loaded window on recreation and the restored offset drifts by
            // a few items — it does not land on the byte-identical pre-rotation item.
            waitUntil(timeoutMillis = 10_000L) {
                onAllNodesWithText(newestText).fetchSemanticsNodes().isEmpty() &&
                    onAllNodesWithText("Conversation message ", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(newestText).assertDoesNotExist()
        }
    }
}
