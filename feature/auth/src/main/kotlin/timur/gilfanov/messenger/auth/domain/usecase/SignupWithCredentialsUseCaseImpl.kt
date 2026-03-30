package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidator
import timur.gilfanov.messenger.auth.domain.validation.ProfileNameValidator
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

class SignupWithCredentialsUseCaseImpl(
    private val validator: CredentialsValidator,
    private val nameValidator: ProfileNameValidator,
    private val repository: AuthRepository,
    private val logger: Logger,
) : SignupWithCredentialsUseCase {

    companion object {
        private const val TAG = "SignupWithCredentialsUseCaseImpl"
    }

    override suspend fun invoke(
        credentials: Credentials,
        name: String,
    ): ResultWithError<Unit, SignupWithCredentialsUseCaseError> {
        val inputError = validateInputs(credentials, name)
        if (inputError != null) {
            return ResultWithError.Failure(inputError)
        }
        return repository.signup(credentials, name).fold(
            onSuccess = { ResultWithError.Success(Unit) },
            onFailure = { error ->
                logger.e(TAG, "Repository signup failed: $error")
                ResultWithError.Failure(error.toUseCaseError())
            },
        )
    }

    private fun validateInputs(
        credentials: Credentials,
        name: String,
    ): SignupWithCredentialsUseCaseError? {
        val credResult = validator.validate(credentials)
        if (credResult is ResultWithError.Failure) {
            return when (val error = credResult.error) {
                is CredentialsValidationError.Email ->
                    SignupWithCredentialsUseCaseError.InvalidEmail(error.reason)
                is CredentialsValidationError.Password ->
                    SignupWithCredentialsUseCaseError.InvalidPassword(error.reason)
            }
        }
        val nameResult = nameValidator.validate(name)
        return if (nameResult is ResultWithError.Failure) {
            SignupWithCredentialsUseCaseError.InvalidName(nameResult.error)
        } else {
            null
        }
    }
}
