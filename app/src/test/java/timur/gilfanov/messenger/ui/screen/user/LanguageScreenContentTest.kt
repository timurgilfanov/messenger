package timur.gilfanov.messenger.ui.screen.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LanguageScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val englishItem = LanguageItem("English", UiLanguage.English)
    private val germanItem = LanguageItem("German", UiLanguage.German)

    private val initialState = LanguageUiState(
        languages = persistentListOf(englishItem, germanItem),
        selectedLanguage = englishItem,
    )

    @Test
    fun `selecting german language updates radio button selection`() {
        composeTestRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            MessengerTheme {
                LanguageScreenContent(
                    uiState = state,
                    onSelectLanguage = { state = state.copy(selectedLanguage = it) },
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
                mutableStateOf(initialState.copy(selectedLanguage = germanItem))
            }
            MessengerTheme {
                LanguageScreenContent(
                    uiState = state,
                    onSelectLanguage = { state = state.copy(selectedLanguage = it) },
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
        var selectedLanguage: LanguageItem? = null

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreenContent(
                    uiState = initialState,
                    onSelectLanguage = { selectedLanguage = it },
                )
            }
        }

        assertEquals(null, selectedLanguage)

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        assertEquals(germanItem, selectedLanguage)
    }

    @Test
    fun `selecting language text calls onSelectLanguage callback`() {
        var selectedLanguage: LanguageItem? = null

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreenContent(
                    uiState = initialState,
                    onSelectLanguage = { selectedLanguage = it },
                )
            }
        }

        assertEquals(null, selectedLanguage)

        composeTestRule.onNodeWithTag("language_text_${UiLanguage.German}")
            .performClick()

        assertEquals(germanItem, selectedLanguage)
    }
}
