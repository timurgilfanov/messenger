package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import kotlin.time.Duration.Companion.minutes
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
import timur.gilfanov.messenger.test.AndroidTestDataHelper.BOB_CHAT_ID
import timur.gilfanov.messenger.test.AndroidTestDataHelper.BOB_TEXT_1
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestDataHelper.MESSAGE_3_TIME
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsHelper
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.ui.activity.MainActivity
import timur.gilfanov.messenger.util.Logger

/**
 * Verifies the chat screen renders messages in chronological order — oldest at the top, newest at
 * the bottom — at the live edge as new messages arrive. Opens the Bob chat (one seeded message),
 * injects a received message and sends a reply, then asserts all three render with strictly
 * ascending vertical positions. Catches a new message appending to the wrong end after PagingSource
 * invalidation, a path preset-only messages can never exercise.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, AuthDataModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class ChatLiveAppendOrderApplicationTest {

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
    object LiveAppendTestRepositoryModule {
        private val repository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

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
    fun applicationTest_chatRendersLiveAppendedMessagesOldestAtTopNewestAtBottom() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"), timeoutMillis = 5_000L)
            waitUntilExactlyOneExists(hasTestTag("chat_item_$BOB_CHAT_ID"), timeoutMillis = 5_000L)

            onNodeWithTag("chat_item_$BOB_CHAT_ID").performClick()

            waitUntilExactlyOneExists(hasTestTag("message_list"), timeoutMillis = 5_000L)
            waitUntilExactlyOneExists(hasText(BOB_TEXT_1), timeoutMillis = 5_000L)

            val receivedText = "Are you free to talk this afternoon?"
            (chatRepository as AndroidTestRepositoryWithRealImplementation)
                .simulateBobSendingMessage(
                    receivedText,
                    createdAt = MESSAGE_3_TIME.plus(1.minutes),
                )
            waitUntilExactlyOneExists(hasText(receivedText), timeoutMillis = 5_000L)

            val replyText = "Sure, let's connect after lunch."
            onNodeWithTag("message_input").performTextInput(replyText)
            onNodeWithTag("send_button").performClick()
            waitUntilExactlyOneExists(hasText(replyText), timeoutMillis = 5_000L)

            val seededTop = onNodeWithText(BOB_TEXT_1).getBoundsInRoot().top
            val receivedTop = onNodeWithText(receivedText).getBoundsInRoot().top
            val replyTop = onNodeWithText(replyText).getBoundsInRoot().top

            assertTrue(
                seededTop < receivedTop,
                "Seeded message must render above the received message " +
                    "(top $seededTop should be less than $receivedTop)",
            )
            assertTrue(
                receivedTop < replyTop,
                "Received message must render above the sent reply " +
                    "(top $receivedTop should be less than $replyTop)",
            )
        }
    }
}
