package timur.gilfanov.messenger.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import timur.gilfanov.messenger.annotations.Screenshot
import timur.gilfanov.messenger.testutil.LocaleTimeZoneRule
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(Screenshot::class)
abstract class ScreenshotTestBase {

    protected val compose = createComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(LocaleTimeZoneRule())
        .around(compose)

    enum class ScreenSize(val width: Int, @Suppress("unused") val qualifier: String) {
        SMALL(320, "w320dp"),
        MEDIUM(360, "w360dp"),
        LARGE(411, "w411dp"),
    }

    enum class Theme(val isDark: Boolean) {
        LIGHT(false),
        DARK(true),
    }

    data class TestConfiguration(
        val screenSize: ScreenSize = ScreenSize.MEDIUM,
        val theme: Theme = Theme.LIGHT,
        val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    )

    protected fun captureScreenshot(
        configuration: TestConfiguration = TestConfiguration(),
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides configuration.layoutDirection) {
                MessengerTheme(darkTheme = configuration.theme.isDark) {
                    Box(
                        Modifier
                            .width(configuration.screenSize.width.dp)
                            .padding(16.dp),
                    ) {
                        content()
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage()
    }
}
