package timur.gilfanov.messenger.feature.auth

import kotlinx.coroutines.CompletableDeferred
import timur.gilfanov.messenger.auth.domain.usecase.LoginUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.Credentials

class LoginWithCredentialsUseCaseStub : LoginWithCredentialsUseCase {
    var shouldSuspend: Boolean = false
    var result: ResultWithError<Unit, LoginUseCaseError>? = null
    var delegate: LoginWithCredentialsUseCase? = null
    private var pendingLogin: CompletableDeferred<Unit>? = null

    fun reset() {
        shouldSuspend = false
        result = null
        delegate = null
        pendingLogin?.cancel()
        pendingLogin = null
    }

    override suspend fun invoke(
        credentials: Credentials,
    ): ResultWithError<Unit, LoginUseCaseError> {
        if (shouldSuspend) {
            val deferred = CompletableDeferred<Unit>()
            pendingLogin = deferred
            deferred.await()
        }
        result?.let { return it }
        return delegate?.invoke(credentials) ?: ResultWithError.Success(Unit)
    }
}
