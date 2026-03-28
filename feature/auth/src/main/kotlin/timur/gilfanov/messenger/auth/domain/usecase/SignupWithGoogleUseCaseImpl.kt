package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.auth.domain.validation.ProfileNameValidator
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

class SignupWithGoogleUseCaseImpl(
    private val nameValidator: ProfileNameValidator,
    private val repository: AuthRepository,
    private val logger: Logger,
) : SignupWithGoogleUseCase {

    companion object {
        private const val TAG = "SignupWithGoogleUseCaseImpl"
    }

    override suspend fun invoke(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<Unit, SignupWithGoogleUseCaseError> {
        val nameValidation = nameValidator.validate(name)
        if (nameValidation is ResultWithError.Failure) {
            return ResultWithError.Failure(
                SignupWithGoogleUseCaseError.InvalidName(nameValidation.error),
            )
        }

        return repository.signupWithGoogle(idToken, name).fold(
            onSuccess = { ResultWithError.Success(Unit) },
            onFailure = { error ->
                logger.e(TAG, "Repository signupWithGoogle failed: $error")
                ResultWithError.Failure(error.toUseCaseError())
            },
        )
    }
}
