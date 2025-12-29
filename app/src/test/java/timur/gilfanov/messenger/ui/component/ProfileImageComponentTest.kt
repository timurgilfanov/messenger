package timur.gilfanov.messenger.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.ui.theme.MessengerTheme
import timur.gilfanov.messenger.util.generateProfileImageUrl

@RunWith(AndroidJUnit4::class)
class ProfileImageComponentTest : Component {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileImage_withNullUrl_showsNameInitial() {
        composeTestRule.setContent {
            MessengerTheme {
                ProfileImageTestWrapper(
                    pictureUrl = null,
                    name = "Alice Smith",
                )
            }
        }

        composeTestRule
            .onNodeWithText("A")
            .assertIsDisplayed()
    }

    @Test
    fun profileImage_withEmptyName_showsPersonIcon() {
        composeTestRule.setContent {
            MessengerTheme {
                ProfileImageTestWrapper(
                    pictureUrl = null,
                    name = "",
                )
            }
        }

        // Since it's an icon, we check that the component is displayed
        composeTestRule
            .onNodeWithTag("profile_image")
            .assertIsDisplayed()
    }

    @Test
    fun profileImage_withUrl_isDisplayed() {
        composeTestRule.setContent {
            MessengerTheme {
                ProfileImageTestWrapper(
                    pictureUrl = generateProfileImageUrl("John Doe"),
                    name = "John Doe",
                )
            }
        }

        composeTestRule
            .onNodeWithTag("profile_image")
            .assertIsDisplayed()
    }

    @Test
    fun profileImage_withLowercaseName_showsUppercaseInitial() {
        composeTestRule.setContent {
            MessengerTheme {
                ProfileImageTestWrapper(
                    pictureUrl = null,
                    name = "bob",
                )
            }
        }

        composeTestRule
            .onNodeWithText("B")
            .assertIsDisplayed()
    }

    @Composable
    private fun ProfileImageTestWrapper(pictureUrl: String?, name: String) {
        Box {
            ProfileImage(
                pictureUrl = pictureUrl,
                name = name,
                size = 48.dp,
                modifier = Modifier.testTag("profile_image"),
            )
        }
    }
}
