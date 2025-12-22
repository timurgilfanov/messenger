package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
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
    fun `shows snackbar when language change fails`() {
        val viewModel = createViewModelWithChangeFailure()

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreen(
                    onAuthFailure = {},
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag("language_radio_${UiLanguage.German}")
            .performClick()

        composeTestRule.waitUntil {
            composeTestRule
                .onAllNodes(hasText("Cannot change language"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Cannot change language").assertExists()
    }

    @Test
    fun `calls onAuthFailure when unauthorized`() {
        var authFailureCalled = false
        val viewModel = createViewModelWithUnauthorized()

        composeTestRule.setContent {
            MessengerTheme {
                LanguageScreen(
                    onAuthFailure = { authFailureCalled = true },
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
