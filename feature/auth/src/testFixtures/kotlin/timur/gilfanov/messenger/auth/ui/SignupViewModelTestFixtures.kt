package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupViewModel
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.auth.validation.ProfileNameValidator
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorImpl
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorStub
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidatorStub
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError

object SignupViewModelTestFixtures {

    fun createViewModel(
        nameValidatorError: ProfileNameValidationError? = null,
        signupWithGoogleResult: ResultWithError<AuthSession, GoogleSignupRepositoryError>? = null,
        credentialsValidatorError: CredentialsValidationError? = null,
        signupWithCredentialsResult: ResultWithError<AuthSession, SignupRepositoryError>? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        viewModelNameValidator: ProfileNameValidator = ProfileNameValidatorImpl(),
        viewModelCredentialsValidator: CredentialsValidator = CredentialsValidatorImpl(),
    ): SignupViewModel {
        val logger = NoOpLogger()
        val nameValidator = ProfileNameValidatorStub(nameValidatorError)
        val credentialsValidator = CredentialsValidatorStub(credentialsValidatorError)
        val repository = AuthRepositoryFake()

        if (signupWithGoogleResult != null) {
            repository.enqueueSignupWithGoogleResult(signupWithGoogleResult)
        }
        if (signupWithCredentialsResult != null) {
            repository.enqueueSignupResult(signupWithCredentialsResult)
        }

        val signupWithGoogle = SignupWithGoogleUseCaseImpl(nameValidator, repository, logger)
        val signupWithCredentials =
            SignupWithCredentialsUseCaseImpl(
                credentialsValidator,
                nameValidator,
                repository,
                logger,
            )

        return SignupViewModel(
            signupWithGoogle,
            signupWithCredentials,
            savedStateHandle,
            viewModelNameValidator,
            viewModelCredentialsValidator,
        )
    }
}
