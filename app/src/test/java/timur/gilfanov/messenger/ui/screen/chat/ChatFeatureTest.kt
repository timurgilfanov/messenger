@file:OptIn(ExperimentalTestApi::class)

package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Feature
import timur.gilfanov.messenger.ChatScreenTestActivity
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    timur.gilfanov.messenger.di.TestUserModule::class,
    timur.gilfanov.messenger.di.TestChatModule::class,
)
@Config(sdk = [33], application = HiltTestApplication::class)
@Category(Feature::class)
class ChatFeatureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object ChatScreenDisplayTestRepositoryModule {
        private val repositoryInstance = TestRepositoryWithRealImplementation()

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository = repositoryInstance

        @Provides
        @Singleton
        fun provideMessageRepository(): MessageRepository = repositoryInstance
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestUserChatModule {
        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestUserId
        fun provideTestUserId(): String = ChatFeatureTestDataHelper.USER_ID

        @Provides
        @Singleton
        @timur.gilfanov.messenger.di.TestChatId
        fun provideTestChatId(): String = ChatFeatureTestDataHelper.ALICE_CHAT_ID
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `chat screen by default have disabled send button`() {
        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("message_input"),
            timeoutMillis = 5_000L,
        )

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
