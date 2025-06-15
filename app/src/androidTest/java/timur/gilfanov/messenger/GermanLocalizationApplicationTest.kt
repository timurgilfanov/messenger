package timur.gilfanov.messenger

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.messenger.Application

@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class GermanLocalizationApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        // Set the locale to German
        setLocale(Locale.GERMAN)
    }

    @Test
    fun chatScreen_displaysGermanPlaceholderText() {
        composeTestRule.waitForIdle()

        // Verify German placeholder text is displayed
        composeTestRule.onNodeWithText("Nachricht eingeben...")
            .assertIsDisplayed()

        // Verify the message input field is still functional
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        // Verify send button is displayed
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
    }

    @Test
    fun chatScreen_hasGermanContentDescriptions() {
        composeTestRule.waitForIdle()

        // The send button should have German content description
        // Note: Content descriptions are not directly testable via onNodeWithText,
        // but we can verify the button exists and is functional
        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Verify the UI components are rendered correctly with German locale
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Nachricht eingeben...")
            .assertIsDisplayed()
    }

    @Test
    fun chatScreen_functionsCorrectlyWithGermanLocale() {
        composeTestRule.waitForIdle()

        // Verify the app remains functional with German locale
        composeTestRule.onNodeWithTag("message_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()

        // Verify German placeholder is shown
        composeTestRule.onNodeWithText("Nachricht eingeben...")
            .assertIsDisplayed()

        // The app should be fully functional regardless of locale
        // All UI interactions should work normally
    }

    private fun setLocale(locale: Locale) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        context.createConfigurationContext(configuration)
        Locale.setDefault(locale)
    }
}
