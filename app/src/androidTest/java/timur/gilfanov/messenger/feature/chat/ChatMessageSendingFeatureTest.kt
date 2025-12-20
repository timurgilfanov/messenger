package timur.gilfanov.messenger.feature.chat

import androidx.compose.ui.semantics.SemanticsProperties.Disabled
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.ChatScreenTestActivity
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.annotations.ReleaseCandidateTest
import timur.gilfanov.messenger.data.repository.DefaultIdentityRepository
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestChatModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class, TestChatModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class ChatMessageSendingFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Inject
    lateinit var chatRepository: ChatRepository

    @get:Rule(order = 2)
    val repositoryCleanupRule = RepositoryCleanupRule(repositoryProvider = { chatRepository })

    @Module
    @InstallIn(SingletonComponent::class)
    object MessageSendingTestRepositoryModule {
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
        fun provideIdentityRepository(): IdentityRepository = DefaultIdentityRepository()

        @Provides
        @Singleton
        fun provideRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob())

        @Provides
        @Singleton
        fun provideLocaleRepository(logger: Logger): LocaleRepository = LocaleRepositoryImpl(logger)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserChatTestModule {
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

    @ReleaseCandidateTest
    @Test
    fun messageSending_completesSuccessfully() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()
            val testMessage = "Hello, this is a test message!"
            onNodeWithTag("message_input").performTextInput(testMessage)
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(testMessage)
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            waitUntil(timeoutMillis = 1_000) {
                onNodeWithTag("message_input")
                    .fetchSemanticsNode()
                    .config.getOrNull(EditableText)
                    ?.text
                    ?.isEmpty() == true
            }
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
        }
    }

    @Test
    fun messageSending_handlesEmptyInput() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            onNodeWithText("Type a message...").assertIsDisplayed()
        }
    }

    @Test
    fun messageSending_multipleMessagesSequentially() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()
            onNodeWithTag("send_button").assertIsDisplayed()
            repeat(100) {
                val message = "Test message #$it"
                onNodeWithTag("message_input").run {
                    performTextInput(message)
                    waitUntil(timeoutMillis = 1_000) {
                        fetchSemanticsNode().config[EditableText].text == message
                    }
                }
                onNodeWithText("Type a message...").assertIsNotDisplayed()
                onNodeWithTag("send_button").let {
                    it.assertIsEnabled()
                    it.performClick()
                }
                waitUntil(timeoutMillis = 1_000) {
                    onNodeWithTag("message_input")
                        .fetchSemanticsNode()
                        .config.run {
                            getOrNull(EditableText)?.text?.isEmpty() == true &&
                                getOrNull(Disabled) == null
                        }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun messageSending_preservesInputDuringTyping() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = 5_000L,
            )
            onNodeWithText("Type a message...").assertIsDisplayed()

            val partialMessage = "This is a long message that I'm typing"
            val completeMessage = "$partialMessage and now I'll send it"

            onNodeWithTag("message_input").performTextInput(partialMessage)
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(partialMessage)
            onNodeWithTag("message_input")
                .performTextInputSelection(TextRange(partialMessage.length))
            onNodeWithTag("message_input").performTextInput(" and now I'll send it")
            waitForIdle()
            onNodeWithTag("message_input").assertTextEquals(completeMessage)
            onNodeWithTag("send_button").performClick()
            waitForIdle()
            waitUntil(timeoutMillis = 1_000) {
                onNodeWithTag("message_input")
                    .fetchSemanticsNode()
                    .config.getOrNull(EditableText)
                    ?.text
                    ?.isEmpty() == true
            }
        }
    }
}
