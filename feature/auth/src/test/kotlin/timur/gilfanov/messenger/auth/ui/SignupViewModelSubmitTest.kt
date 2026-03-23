package timur.gilfanov.messenger.auth.ui

import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelSubmitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testIdToken = "test-google-id-token"

    @Test
    fun `name validation failure sets nameError`() = runTest {
        val validationError = ProfileNameValidationError.LengthOutOfBounds(
            length = 0,
            min = 1,
            max = 50,
        )
        val viewModel = createViewModel(nameValidatorError = validationError)

        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()

        assertIs<ProfileNameValidationError.LengthOutOfBounds>(viewModel.state.value.nameError)
    }

    @Test
    fun `updateName clears nameError`() = runTest {
        val validationError = ProfileNameValidationError.LengthOutOfBounds(
            length = 0,
            min = 1,
            max = 50,
        )
        val viewModel = createViewModel(nameValidatorError = validationError)

        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<ProfileNameValidationError.LengthOutOfBounds>(viewModel.state.value.nameError)

        viewModel.updateName("Alice")
        assertNull(viewModel.state.value.nameError)
    }
}
