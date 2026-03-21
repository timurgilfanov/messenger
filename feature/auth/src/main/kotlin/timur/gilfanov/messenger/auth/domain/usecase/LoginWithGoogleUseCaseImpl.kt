package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

class LoginWithGoogleUseCaseImpl(
    private val repository: AuthRepository,
    private val logger: Logger,
) : LoginWithGoogleUseCase {

    companion object {
        private const val TAG = "LoginWithGoogleUseCaseImpl"
    }

    override suspend fun invoke(
        idToken: GoogleIdToken,
    ): ResultWithError<Unit, GoogleLoginUseCaseError> = repository.loginWithGoogle(idToken).fold(
        onSuccess = { ResultWithError.Success(Unit) },
        onFailure = { error ->
            logger.e(TAG, "Repository loginWithGoogle failed: $error")
            ResultWithError.Failure(error.toUseCaseError())
        },
    )
}
