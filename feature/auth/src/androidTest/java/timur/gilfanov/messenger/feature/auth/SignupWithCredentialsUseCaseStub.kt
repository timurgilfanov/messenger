package timur.gilfanov.messenger.feature.auth

import kotlinx.coroutines.CompletableDeferred
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithCredentialsUseCaseError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

class SignupWithCredentialsUseCaseStub : SignupWithCredentialsUseCase {
    var shouldSuspend: Boolean = false
    var result: ResultWithError<Unit, SignupWithCredentialsUseCaseError>? = null
    private var pendingSignup: CompletableDeferred<Unit>? = null

    fun reset() {
        shouldSuspend = false
        result = null
        pendingSignup?.cancel()
        pendingSignup = null
    }

    override suspend fun invoke(
        credentials: Credentials,
        name: String,
    ): ResultWithError<Unit, SignupWithCredentialsUseCaseError> {
        if (shouldSuspend) {
            val deferred = CompletableDeferred<Unit>()
            pendingSignup = deferred
            deferred.await()
        }
        return result ?: ResultWithError.Success(Unit)
    }
}
