package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays loading when both states are Loading`() {
        composeTestRule.setContent {
            MessengerTheme {
                SettingsScreenContent(
                    profileContent = { TestProfileContent() },
                    settingsUiState = SettingsUiState.Loading,
                    onChangeLanguageClick = {},
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("profile_loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_loading").assertIsDisplayed()
    }

    @Test
    fun `displays language item when settings state is Ready`() {
        composeTestRule.setContent {
            MessengerTheme {
                SettingsScreenContent(
                    profileContent = { TestProfileContent() },
                    settingsUiState = SettingsUiState.Ready(SettingsUi(UiLanguage.English)),
                    onChangeLanguageClick = {},
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_language_item").assertIsDisplayed()
    }

    @Test
    fun `displays correct language value for English`() {
        composeTestRule.setContent {
            MessengerTheme {
                SettingsScreenContent(
                    profileContent = { TestProfileContent() },
                    settingsUiState = SettingsUiState.Ready(SettingsUi(UiLanguage.English)),
                    onChangeLanguageClick = {},
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun `displays correct language value for German`() {
        composeTestRule.setContent {
            MessengerTheme {
                SettingsScreenContent(
                    profileContent = { TestProfileContent() },
                    settingsUiState = SettingsUiState.Ready(SettingsUi(UiLanguage.German)),
                    onChangeLanguageClick = {},
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("German").assertIsDisplayed()
    }

    @Test
    fun `clicking language item calls onChangeLanguageClick callback`() {
        var languageClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                SettingsScreenContent(
                    profileContent = { TestProfileContent() },
                    settingsUiState = SettingsUiState.Ready(SettingsUi(UiLanguage.English)),
                    onChangeLanguageClick = { languageClicked = true },
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_language_item").performClick()

        assertTrue(languageClicked)
    }

    @Composable
    private fun TestProfileContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .testTag("profile_loading"),
        )
    }
}
