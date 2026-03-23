package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

fun interface SignupWithGoogleUseCase {
    suspend operator fun invoke(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<Unit, SignupWithGoogleUseCaseError>
}
