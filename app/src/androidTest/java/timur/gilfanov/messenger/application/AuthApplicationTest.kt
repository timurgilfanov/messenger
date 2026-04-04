package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.ApplicationTest
import timur.gilfanov.messenger.auth.di.AuthDataModule
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper.ALICE_CHAT_ID
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.ui.activity.MainActivity

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, AuthDataModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class AuthApplicationTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
        private const val TEST_EMAIL = "user@example.com"
        private const val TEST_PASSWORD = "Password1"
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
    object AuthTestRepositoryModule {
        private val repository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = repository

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = repository

        @Provides
        @Singleton
        fun provideSettingsRepository(): SettingsRepository = SettingsRepositoryStub()

        @Provides
        @Singleton
        fun provideAuthRepository(): AuthRepository = AuthRepositoryFake()

        @Provides
        @Singleton
        fun provideRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob())

        @Provides
        @Singleton
        fun provideLocaleRepository(): LocaleRepository = object : LocaleRepository {
            override suspend fun applyLocale(language: UiLanguage) = Unit
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun authFlow_loginThenNavigateToChatThenLogoutThenLoginScreenShown() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_screen").assertIsDisplayed()

            onNodeWithTag("login_email_field").performTextInput(TEST_EMAIL)
            onNodeWithTag("login_password_field").performTextInput(TEST_PASSWORD)
            onNodeWithTag("login_sign_in_button").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("chat_list").assertIsDisplayed()

            waitUntilExactlyOneExists(hasTestTag("chat_item_${ALICE_CHAT_ID}"))
            onNodeWithTag("chat_item_${ALICE_CHAT_ID}").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("message_input"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            onNodeWithTag("bottom_nav_settings").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("settings_logout_item"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("settings_logout_item").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("login_screen"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )
            onNodeWithTag("login_screen").assertIsDisplayed()
        }
    }
}
