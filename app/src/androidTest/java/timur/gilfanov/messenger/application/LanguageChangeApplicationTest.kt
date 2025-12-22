package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsRepository
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class LanguageChangeApplicationTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
        @Singleton
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
    object TestUserLanguageChangeTestModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestUserId
        fun provideTestUserId(): String = AndroidTestDataHelper.USER_ID
    }

    @Test
    fun applicationTest_userCanChangeLanguageToGermanThroughSettings() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithText("Sprache").assertDoesNotExist()
            onNodeWithTag("bottom_nav_settings").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("settings_language_item"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithText("Sprache").assertDoesNotExist()
            onNodeWithTag("settings_language_item").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("language_radio_German"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithText("Sprache").assertDoesNotExist()
            onNodeWithTag("language_radio_German").performClick()

            // Verify UI changed to German - "Sprache" is German for "Language"
            waitUntilExactlyOneExists(
                hasText("Sprache"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            onNodeWithTag("language_back_button").performClick()
            waitUntilExactlyOneExists(
                hasTestTag("settings_language_item"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            // Verify settings screen also shows German text
            onNodeWithText("Sprache").assertIsDisplayed()
        }
    }
}
