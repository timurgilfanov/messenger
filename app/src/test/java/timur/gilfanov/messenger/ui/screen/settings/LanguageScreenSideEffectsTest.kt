package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.ui.test.junit4.createComposeRule
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
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSettingsRepositoryFake
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createSuccessfulIdentityRepository
import timur.gilfanov.messenger.ui.screen.settings.LanguageViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(Component::class)
class LanguageScreenSideEffectsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `calls onShowSnackbar when language change fails`() {
        var snackbarMessage: String? = null
        val viewModel = createViewModelWithChangeFailure()

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreen(
                    onAuthFailure = {},
                    onShowSnackbar = { snackbarMessage = it },
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        composeTestRule.waitUntil { snackbarMessage != null }

        assertEquals("Cannot change language", snackbarMessage)
    }

    @Test
    fun `calls onAuthFailure when unauthorized`() {
        var authFailureCalled = false
        val viewModel = createViewModelWithUnauthorized()

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreen(
                    onAuthFailure = { authFailureCalled = true },
                    onShowSnackbar = {},
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        composeTestRule.waitUntil { authFailureCalled }

        assertTrue(authFailureCalled)
    }

    private fun createViewModelWithChangeFailure(): LanguageViewModel {
        val identityRepository = createSuccessfulIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(
            currentLanguage = UiLanguage.English,
            changeResult = ResultWithError.Failure(
                ChangeLanguageRepositoryError.Recoverable.InsufficientStorage,
            ),
        )
        return createViewModel(identityRepository, settingsRepository)
    }

    private fun createViewModelWithUnauthorized(): LanguageViewModel {
        val identityRepository = LanguageViewModelTestFixtures.createFailingIdentityRepository()
        val settingsRepository = createSettingsRepositoryFake(UiLanguage.English)
        return createViewModel(identityRepository, settingsRepository)
    }
}
