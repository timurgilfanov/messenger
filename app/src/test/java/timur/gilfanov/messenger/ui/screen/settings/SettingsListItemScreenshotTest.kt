package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Test
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

@Config(sdk = [33], qualifiers = "en-rUS-w411dp-h640dp-xxhdpi")
class SettingsListItemScreenshotTest : ScreenshotTestBase() {

    @Test
    fun default_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SettingsListItem()
        }
    }

    @Test
    fun default_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SettingsListItem()
        }
    }

    @Test
    fun longTitle_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SettingsListItem(
                title = "This is a very long settings title that should not wrap to multiple lines",
                value = "English",
            )
        }
    }

    @Test
    fun longValue_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SettingsListItem(
                title = "Language",
                value = "This is a very long settings value that should not wrap to multiple lines",
            )
        }
    }

    @Test
    fun smallScreen_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.LIGHT,
            ),
        ) {
            SettingsListItem()
        }
    }

    @Test
    fun largeScreen_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.LIGHT,
            ),
        ) {
            SettingsListItem()
        }
    }

    @Test
    fun rtl_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            SettingsListItem(
                title = "اللغة",
                value = "العربية",
            )
        }
    }
}

@Composable
private fun SettingsListItem(title: String = "Language", value: String = "English") {
    SettingsListItem(
        title = title,
        value = value,
        action = {},
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    )
}
