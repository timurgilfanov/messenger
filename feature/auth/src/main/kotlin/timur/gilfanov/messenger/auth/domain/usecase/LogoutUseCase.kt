package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Cross-layer logout operation that clears the local session and revokes server-side tokens.
 */
fun interface LogoutUseCase {
    /**
     * Clears the local session and revokes server-side tokens.
     * Returns [ResultWithError.Success] on completion, or [ResultWithError.Failure] with
     * [LogoutError.LocalOperationFailed] or [LogoutError.RemoteOperationFailed] on failure.
     */
    suspend operator fun invoke(): ResultWithError<Unit, LogoutError>
}
