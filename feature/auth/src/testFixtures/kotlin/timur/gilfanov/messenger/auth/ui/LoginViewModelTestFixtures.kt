package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.ui.screen.login.LoginViewModel
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidatorStub
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError

object LoginViewModelTestFixtures {

    fun createViewModel(
        validatorError: CredentialsValidationError? = null,
        loginResult: ResultWithError<AuthSession, LoginRepositoryError>? = null,
        googleLoginResult: ResultWithError<AuthSession, GoogleLoginRepositoryError>? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): LoginViewModel {
        val logger = NoOpLogger()
        val validator = CredentialsValidatorStub(validatorError)
        val repository = AuthRepositoryFake()

        if (loginResult != null) {
            repository.enqueueLoginWithCredentialsResult(loginResult)
        }
        if (googleLoginResult != null) {
            repository.enqueueLoginWithGoogleResult(googleLoginResult)
        }

        val loginWithCredentials = LoginWithCredentialsUseCaseImpl(validator, repository, logger)
        val loginWithGoogle = LoginWithGoogleUseCaseImpl(repository, logger)

        return LoginViewModel(loginWithCredentials, loginWithGoogle, savedStateHandle)
    }
}
