package timur.gilfanov.messenger.ui.screen.chatlist

import org.junit.Test
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

class EmptyStateComponentScreenshotTest : ScreenshotTestBase() {

    @Test
    fun default_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            EmptyStateComponent()
        }
    }

    @Test
    fun default_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            EmptyStateComponent()
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
            EmptyStateComponent()
        }
    }

    @Test
    fun smallScreen_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.DARK,
            ),
        ) {
            EmptyStateComponent()
        }
    }

    @Test
    fun mediumScreen_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.MEDIUM,
                theme = Theme.LIGHT,
            ),
        ) {
            EmptyStateComponent()
        }
    }

    @Test
    fun mediumScreen_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.MEDIUM,
                theme = Theme.DARK,
            ),
        ) {
            EmptyStateComponent()
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
            EmptyStateComponent()
        }
    }

    @Test
    fun largeScreen_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.DARK,
            ),
        ) {
            EmptyStateComponent()
        }
    }

    @Test
    fun rtl_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            EmptyStateComponent()
        }
    }

    @Test
    fun rtl_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            EmptyStateComponent()
        }
    }
}
