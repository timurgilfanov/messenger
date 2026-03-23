package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupViewModel
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorStub
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

object SignupViewModelTestFixtures {

    fun createViewModel(
        nameValidatorError: ProfileNameValidationError? = null,
        signupResult: ResultWithError<AuthSession, GoogleSignupRepositoryError>? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SignupViewModel {
        val logger = NoOpLogger()
        val nameValidator = ProfileNameValidatorStub(nameValidatorError)
        val repository = AuthRepositoryFake()

        if (signupResult != null) {
            repository.enqueueSignupWithGoogleResult(signupResult)
        }

        val signupWithGoogle = SignupWithGoogleUseCaseImpl(nameValidator, repository, logger)

        return SignupViewModel(signupWithGoogle, savedStateHandle)
    }
}
