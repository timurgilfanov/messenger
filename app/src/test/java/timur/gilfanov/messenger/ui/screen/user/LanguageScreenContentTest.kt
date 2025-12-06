package timur.gilfanov.messenger.ui.screen.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.uiLanguageList
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LanguageScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val initialState = LanguageUiState(
        languages = uiLanguageList,
        selectedLanguage = UiLanguage.English,
    )

    @Test
    fun `selecting german language updates radio button selection`() {
        composeTestRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            MessengerTheme {
                LanguageScreenContent(
                    uiState = state,
                    onSelectLanguage = { state = state.copy(selectedLanguage = it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.English}")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .assertIsNotSelected()

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.English}")
            .assertIsNotSelected()
    }

    @Test
    fun `selecting english language after german updates radio button selection`() {
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(initialState.copy(selectedLanguage = UiLanguage.German))
            }
            MessengerTheme {
                LanguageScreenContent(
                    uiState = state,
                    onSelectLanguage = { state = state.copy(selectedLanguage = it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .assertIsSelected()

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.English}")
            .performClick()

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.English}")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .assertIsNotSelected()
    }

    @Test
    fun `selecting language radio button calls onSelectLanguage callback`() {
        var selectedLanguage: UiLanguage? = null

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreenContent(
                    uiState = initialState,
                    onSelectLanguage = { selectedLanguage = it },
                    onBackClick = {},
                )
            }
        }

        assertEquals(null, selectedLanguage)

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        assertEquals(UiLanguage.German, selectedLanguage)
    }

    @Test
    fun `selecting language text calls onSelectLanguage callback`() {
        var selectedLanguage: UiLanguage? = null

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreenContent(
                    uiState = initialState,
                    onSelectLanguage = { selectedLanguage = it },
                    onBackClick = {},
                )
            }
        }

        assertEquals(null, selectedLanguage)

        composeTestRule.onNodeWithTag("language_text_${UiLanguage.German}")
            .performClick()

        assertEquals(UiLanguage.German, selectedLanguage)
    }

    @Test
    fun `clicking back button calls onBackClick callback`() {
        var backClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreenContent(
                    uiState = initialState,
                    onSelectLanguage = {},
                    onBackClick = { backClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back")
            .performClick()

        assertTrue(backClicked)
    }
}
