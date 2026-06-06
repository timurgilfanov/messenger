package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.ApplicationTest
import timur.gilfanov.messenger.auth.di.AuthDataModule
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.LONG_CONVERSATION
import timur.gilfanov.messenger.test.AndroidTestDataHelper.LONG_CHAT_ID
import timur.gilfanov.messenger.test.AndroidTestDataHelper.longConversationMessageText
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsHelper
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.ui.activity.MainActivity
import timur.gilfanov.messenger.util.Logger

/**
 * Verifies the chat screen renders preset messages in chronological order — oldest at the top,
 * newest at the bottom — on initial paging load. Opens a chat already seeded with 40 messages at
 * strictly ascending timestamps and asserts the three newest visible messages render with strictly
 * ascending vertical positions. Directly fails on a newest-first initial render regression.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, AuthDataModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class ChatMessageOrderApplicationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var chatRepository: ChatRepository

    @get:Rule(order = 2)
    val repositoryCleanupRule = RepositoryCleanupRule(repositoryProvider = { chatRepository })

    @Module
    @InstallIn(SingletonComponent::class)
    object LongConversationTestRepositoryModule {
        private val repository = AndroidTestRepositoryWithRealImplementation(LONG_CONVERSATION)

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

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun applicationTest_chatRendersPresetMessagesOldestAtTopNewestAtBottom() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"), timeoutMillis = 5_000L)
            waitUntilExactlyOneExists(hasTestTag("chat_item_$LONG_CHAT_ID"), timeoutMillis = 5_000L)

            onNodeWithTag("chat_item_$LONG_CHAT_ID").performClick()

            waitUntilExactlyOneExists(hasTestTag("message_list"), timeoutMillis = 5_000L)

            val oldestVisibleText = longConversationMessageText(38)
            val middleVisibleText = longConversationMessageText(39)
            val newestVisibleText = longConversationMessageText(40)

            waitUntilExactlyOneExists(hasText(oldestVisibleText), timeoutMillis = 5_000L)
            waitUntilExactlyOneExists(hasText(middleVisibleText), timeoutMillis = 5_000L)
            waitUntilExactlyOneExists(hasText(newestVisibleText), timeoutMillis = 5_000L)

            onNodeWithText(oldestVisibleText).assertIsDisplayed()
            onNodeWithText(middleVisibleText).assertIsDisplayed()
            onNodeWithText(newestVisibleText).assertIsDisplayed()

            val oldestTop = onNodeWithText(oldestVisibleText).getBoundsInRoot().top
            val middleTop = onNodeWithText(middleVisibleText).getBoundsInRoot().top
            val newestTop = onNodeWithText(newestVisibleText).getBoundsInRoot().top

            assertTrue(
                oldestTop < middleTop,
                "Message 38 must render above message 39 " +
                    "(top $oldestTop should be less than $middleTop)",
            )
            assertTrue(
                middleTop < newestTop,
                "Message 39 must render above message 40 " +
                    "(top $middleTop should be less than $newestTop)",
            )
        }
    }
}
