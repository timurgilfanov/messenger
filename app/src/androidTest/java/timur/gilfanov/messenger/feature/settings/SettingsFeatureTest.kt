package timur.gilfanov.messenger.feature.settings

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.SettingsScreenTestActivity
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.data.repository.DefaultIdentityRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.di.TestUserModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.test.AndroidTestDataHelper
import timur.gilfanov.messenger.test.AndroidTestSettingsRepository
import timur.gilfanov.messenger.test.ChatRepositoryStub
import timur.gilfanov.messenger.test.MessageRepositoryStub
import timur.gilfanov.messenger.test.RepositoryCleanupRule

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, TestUserModule::class)
@FeatureTest
@RunWith(AndroidJUnit4::class)
class SettingsFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<SettingsScreenTestActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @get:Rule(order = 2)
    val repositoryCleanupRule = RepositoryCleanupRule(repositoryProvider = { settingsRepository })

    @Module
    @InstallIn(SingletonComponent::class)
    object SettingsRepositoryTestModule {
        private val repository = AndroidTestSettingsRepository()

        @Provides
        @Singleton
        fun provideSettingsRepository(): SettingsRepository = repository

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = ChatRepositoryStub()

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = MessageRepositoryStub()

        @Provides
        fun provideIdentityRepository(): IdentityRepository = DefaultIdentityRepository()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserTestModule {
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
    fun settingsScreen_displaysLanguageSettingWithEnglish() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("settings_language_item"),
            timeoutMillis = 5_000L,
        )
        composeTestRule.onNodeWithTag("settings_loading").assertDoesNotExist()
    }

    @Test
    fun settingsScreen_handlesRotation() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("settings_language_item"),
                timeoutMillis = 5_000L,
            )

            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            composeTestRule.onNodeWithTag("settings_language_item").assertExists()
        }
    }

    @Test
    fun settingsScreen_handlesMultipleActivityRecreation() = runTest {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("settings_language_item"),
                timeoutMillis = 5_000L,
            )

            repeat(100) {
                withContext(Dispatchers.Main) {
                    composeTestRule.activity.recreate()
                }

                composeTestRule.onNodeWithTag("settings_language_item").assertExists()
            }
        }
    }
}
