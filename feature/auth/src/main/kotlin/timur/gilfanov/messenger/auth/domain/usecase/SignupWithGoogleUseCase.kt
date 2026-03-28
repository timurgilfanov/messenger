package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken

/**
 * Registers a new account using a Google ID token and a profile name.
 *
 * Validates [name] client-side before submitting to the server. On success the authenticated
 * session is persisted and [timur.gilfanov.messenger.domain.usecase.auth.AuthRepository.authState]
 * is updated to [timur.gilfanov.messenger.domain.entity.auth.AuthState.Authenticated].
 */
fun interface SignupWithGoogleUseCase {
    suspend operator fun invoke(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<Unit, SignupWithGoogleUseCaseError>
}
