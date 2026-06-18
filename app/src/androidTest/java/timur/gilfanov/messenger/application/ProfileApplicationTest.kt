package timur.gilfanov.messenger.application

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.annotations.ApplicationTest
import timur.gilfanov.messenger.auth.di.AuthDataModule
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.profile.data.repository.ProfileRepositoryImpl
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSourceImpl
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSourceFake
import timur.gilfanov.messenger.profile.di.ProfileDataModule
import timur.gilfanov.messenger.test.AndroidTestDataHelper.DataScenario.NON_EMPTY
import timur.gilfanov.messenger.test.AndroidTestRepositoryWithRealImplementation
import timur.gilfanov.messenger.test.AndroidTestSettingsHelper
import timur.gilfanov.messenger.test.RepositoryCleanupRule
import timur.gilfanov.messenger.test.SettingsRepositoryStub
import timur.gilfanov.messenger.ui.activity.MainActivity
import timur.gilfanov.messenger.util.Logger

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, AuthDataModule::class, ProfileDataModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class ProfileApplicationTest {

    companion object {
        private const val SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L
        private const val PROFILE_NAME = "Timur Gilfanov"
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
    object ProfileTestRepositoryModule {
        private val repository = AndroidTestRepositoryWithRealImplementation(NON_EMPTY)

        private val profileRepository: ProfileRepository by lazy {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, MessengerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val logger: Logger = TestLogger()
            ProfileRepositoryImpl(
                localDataSource = LocalProfileDataSourceImpl(
                    profileDao = database.profileDao(),
                    logger = logger,
                ),
                remoteDataSource = RemoteProfileDataSourceFake(
                    Profile(name = PROFILE_NAME, pictureUrl = null),
                ),
                logger = logger,
            )
        }

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
        fun provideAuthRepository(): AuthRepository =
            AuthRepositoryFake(AndroidTestSettingsHelper.testSession)

        @Provides
        @Singleton
        fun provideProfileRepository(): ProfileRepository = profileRepository

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
    fun applicationTest_userOpensProfileAndSeesNameAndAvatar() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(
                hasTestTag("chat_list"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            onNodeWithTag("bottom_nav_settings").performClick()

            waitUntilExactlyOneExists(
                hasTestTag("profile_ready"),
                timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS,
            )

            onNodeWithTag("profile_name").assertIsDisplayed()
            onNodeWithTag("profile_name").assertTextEquals(PROFILE_NAME)
            onNodeWithTag("profile_avatar").assertIsDisplayed()
        }
    }
}
