package timur.gilfanov.messenger

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatListConfigurationChangeApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Test
    fun chatListScreen_handlesRotations() {
        with(composeTestRule) {
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            onNodeWithTag("search_button")
                .assertExists()

            onNodeWithTag("new_chat_button")
                .assertExists()

            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE

            onNodeWithTag("search_button")
                .assertExists()

            onNodeWithTag("new_chat_button")
                .assertExists()
        }
    }
}
