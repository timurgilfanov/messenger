package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsRepository
import timur.gilfanov.messenger.test.RepositoryCleanupRule

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class LanguageChangeApplicationTest {

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
    object LanguageChangeTestRepositoryModule {
        private val chatMessageRepository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

        private val settingsRepository by lazy { AndroidTestSettingsRepository() }

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = chatMessageRepository

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = chatMessageRepository

        @Provides
        @Singleton
        fun provideSettingsRepository(): SettingsRepository = settingsRepository

        @Provides
        fun provideIdentityRepository(): IdentityRepository = DefaultIdentityRepository()

        @Provides
        @Singleton
        fun provideRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob())
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserLanguageChangeTestModule {
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
    fun applicationTest_userCanChangeLanguageToGermanThroughSettings() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"), timeoutMillis = 5_000L)

            onNodeWithTag("bottom_nav_settings").performClick()

            waitUntilExactlyOneExists(hasTestTag("settings_language_item"), timeoutMillis = 5_000L)
            onNodeWithTag("settings_language_item").performClick()

            waitUntilExactlyOneExists(hasTestTag("language_radio_German"), timeoutMillis = 5_000L)
            onNodeWithTag("language_radio_German").performClick()

            onNodeWithTag("language_back_button").performClick()
            waitUntilExactlyOneExists(hasTestTag("settings_language_item"), timeoutMillis = 5_000L)

            onNodeWithTag("bottom_nav_chats").performClick()
            waitUntilExactlyOneExists(hasTestTag("chat_list"), timeoutMillis = 5_000L)

            // TODO: Uncomment when language change is implemented
            // After selecting German, the UI should display German strings

            // On LanguageScreen - verify title changed to "Sprache"
            // waitUntilExactlyOneExists(hasText("Sprache"))

            // On SettingsScreen - verify language item title changed
            // onNodeWithText("Sprache").assertIsDisplayed()

            // On MainScreen - verify bottom nav changed (requires string resources)
            // Note: Bottom nav labels are currently hardcoded, would need localization first

            // Verify locale configuration changed
            // activityRule.scenario.onActivity { activity ->
            //     val locale = activity.resources.configuration.locales[0]
            //     assertEquals(java.util.Locale.GERMAN, locale)
            // }
        }
    }
}
