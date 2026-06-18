package timur.gilfanov.messenger.ui.screen.profile

import org.junit.Test
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.profile.ui.screen.ProfileContent
import timur.gilfanov.messenger.profile.ui.screen.ProfileUi
import timur.gilfanov.messenger.profile.ui.screen.ProfileUiState
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

@Config(sdk = [33], qualifiers = "en-rUS-w411dp-h640dp-xxhdpi")
class ProfileContentScreenshotTest : ScreenshotTestBase() {

    @Test
    fun ready_light() {
        captureScreenshot(configuration = TestConfiguration(theme = Theme.LIGHT)) {
            ProfileContent(
                uiState = ProfileUiState.Ready(ProfileUi("Timur", null)),
                onProfileEditClick = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun ready_dark() {
        captureScreenshot(configuration = TestConfiguration(theme = Theme.DARK)) {
            ProfileContent(
                uiState = ProfileUiState.Ready(ProfileUi("Timur", null)),
                onProfileEditClick = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun loading_light() {
        captureScreenshot(configuration = TestConfiguration(theme = Theme.LIGHT)) {
            ProfileContent(
                uiState = ProfileUiState.Loading,
                onProfileEditClick = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun error_light() {
        captureScreenshot(configuration = TestConfiguration(theme = Theme.LIGHT)) {
            ProfileContent(
                uiState = ProfileUiState.Error,
                onProfileEditClick = {},
                onRetry = {},
            )
        }
    }
}
