package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

/**
 * Registers a new account using email/password credentials and a profile name.
 *
 * Validates [credentials] and [name] client-side before submitting to the server. On success the
 * authenticated session is persisted and
 * [timur.gilfanov.messenger.domain.usecase.auth.AuthRepository.authState] is updated to
 * [timur.gilfanov.messenger.domain.entity.auth.AuthState.Authenticated].
 */
fun interface SignupWithCredentialsUseCase {
    suspend operator fun invoke(
        credentials: Credentials,
        name: String,
    ): ResultWithError<Unit, SignupWithCredentialsUseCaseError>
}
