package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.MainActivity
import timur.gilfanov.messenger.annotations.ApplicationTest
import timur.gilfanov.messenger.data.repository.DefaultIdentityRepository
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.ALICE_CHAT_ID
import timur.gilfanov.messenger.test.AndroidTestDataHelper.ALICE_TEXT_1
import timur.gilfanov.messenger.test.AndroidTestDataHelper.BOB_CHAT_ID
import timur.gilfanov.messenger.test.AndroidTestDataHelper.BOB_TEXT_1
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class NavigationApplicationTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
    }

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
    object NavigationTestRepositoryModule {
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
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

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
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            waitUntilExactlyOneExists(hasTestTag("chat_item_${BOB_CHAT_ID}"))

            onNodeWithTag("chat_item_${ALICE_CHAT_ID}").performClick()
            waitUntilExactlyOneExists(hasTextExactly(ALICE_TEXT_1))
            onNodeWithText(ALICE_TEXT_1).assertIsDisplayed()

            activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            waitUntilExactlyOneExists(hasTestTag("chat_item_${BOB_CHAT_ID}"))
            onNodeWithTag("chat_item_${BOB_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(BOB_TEXT_1))
            onNodeWithText(BOB_TEXT_1).assertIsDisplayed()
        }
    }

    @Test
    fun applicationTest_tabsNavigationIsHiddenOnChatScreen() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("bottom_nav").assertIsDisplayed()
            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            onNodeWithText("Alice").assertIsDisplayed()
            onNodeWithText("Bob").assertIsDisplayed()
            onNodeWithTag("chat_item_${ALICE_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(hasTextExactly(ALICE_TEXT_1))
            onNodeWithText(ALICE_TEXT_1).assertIsDisplayed()
            onNodeWithTag("bottom_nav").assertDoesNotExist()
        }
    }

    @Test
    fun applicationTest_tabsNavigationIsHiddenOnSettingsLanguageScreen() {
        with(composeTestRule) {
            onNodeWithTag("bottom_nav").assertIsDisplayed()
            onNodeWithTag("bottom_nav_settings").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("settings_language_item"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("settings_language_item").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("language_radio_German"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("bottom_nav").assertDoesNotExist()
        }
    }
}
