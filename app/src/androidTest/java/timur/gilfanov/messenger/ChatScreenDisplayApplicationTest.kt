package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.annotations.ApplicationTest
import timur.gilfanov.messenger.data.repository.WithChatsParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@ApplicationTest
@RunWith(AndroidJUnit4::class)
class ChatScreenDisplayApplicationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object ChatScreenDisplayTestRepositoryModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = WithChatsParticipantRepository()
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatScreen_inputAreaDisplaysCorrectly() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("message_input"))

        // Verify the message input field is displayed
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Type a message...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    /* todo: this test is flaky, needs investigation
    androidx.compose.ui.test.ComposeTimeoutException: Condition (exactly 1 nodes match (TestTag = 'loading_indicator')) still not satisfied after 1000 ms
	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:441)
     */
    @Test
    fun chatScreen_showsLoadingStateInitially() {
        // The loading state should be visible briefly when the app starts
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("loading_indicator"))
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("message_input"))
    }
}
