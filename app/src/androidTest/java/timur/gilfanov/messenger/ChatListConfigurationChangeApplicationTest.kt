package timur.gilfanov.messenger

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    /* todo: this test is flaky, needs investigation
    java.lang.IllegalStateException: No compose hierarchies found in the app. Possible reasons include: (1) the Activity that calls setContent did not launch; (2) setContent was not called; (3) setContent was called before the ComposeTestRule ran. If setContent is called by the Activity, make sure the Activity is launched after the ComposeTestRule runs
	at androidx.compose.ui.test.TestContext.getAllSemanticsNodes$ui_test_release(TestOwner.kt:87)
     */
    @Test
    fun chatListScreen_handlesMultipleRotations() {
        with(composeTestRule) {
            repeat(100) { index ->
                waitUntilExactlyOneExists(
                    hasTestTag("empty_state")
                        or hasTestTag("chat_list"),
                )

                activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
    }
}
