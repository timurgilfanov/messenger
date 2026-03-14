package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for email/password login repository operations.
 *
 * ## Logical Errors
 * - [InvalidCredentials] - The provided credentials do not match any account
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LoginRepositoryError {
    data object InvalidCredentials : LoginRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : LoginRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : LoginRepositoryError
}
