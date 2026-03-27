package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

class LoginWithCredentialsUseCaseImpl(
    private val validator: CredentialsValidator,
    private val repository: AuthRepository,
    private val logger: Logger,
) : LoginWithCredentialsUseCase {

    companion object {
        private const val TAG = "LoginWithCredentialsUseCaseImpl"
    }

    override suspend fun invoke(
        credentials: Credentials,
    ): ResultWithError<Unit, LoginUseCaseError> {
        val validationResult = validator.validate(credentials)
        if (validationResult is ResultWithError.Failure) {
            return when (val error = validationResult.error) {
                is CredentialsValidationError.Email ->
                    ResultWithError.Failure(LoginUseCaseError.InvalidEmail(error.toUseCaseError()))
                is CredentialsValidationError.Password ->
                    ResultWithError.Failure(
                        LoginUseCaseError.InvalidPassword(error.toUseCaseError()),
                    )
            }
        }
        return repository.loginWithCredentials(credentials).fold(
            onSuccess = { ResultWithError.Success(Unit) },
            onFailure = { error ->
                logger.e(TAG, "Repository loginWithCredentials failed: $error")
                ResultWithError.Failure(error.toUseCaseError())
            },
        )
    }
}
