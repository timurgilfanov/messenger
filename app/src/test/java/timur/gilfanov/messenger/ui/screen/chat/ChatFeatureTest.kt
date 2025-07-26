@file:OptIn(ExperimentalTestApi::class)

package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Feature
import timur.gilfanov.messenger.ChatScreenTestActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(Feature::class)
class ChatFeatureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ChatScreenTestActivity>()

    @Test
    fun `chat screen by default have disabled send button`() {
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("message_input"))

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
