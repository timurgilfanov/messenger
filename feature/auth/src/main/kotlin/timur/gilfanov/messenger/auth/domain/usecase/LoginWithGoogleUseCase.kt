package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

fun interface LoginWithGoogleUseCase {
    suspend operator fun invoke(
        idToken: GoogleIdToken,
    ): ResultWithError<Unit, GoogleLoginUseCaseError>
}
