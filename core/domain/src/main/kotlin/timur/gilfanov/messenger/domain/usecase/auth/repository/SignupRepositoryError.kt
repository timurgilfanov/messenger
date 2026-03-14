package timur.gilfanov.messenger.domain.usecase.auth.repository

import kotlinx.collections.immutable.ImmutableList
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for account signup repository operations.
 *
 * ## Logical Errors
 * - [EmailTaken] - An account with the given email already exists
 * - [InvalidName] - The provided profile name is not acceptable
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupRepositoryError {
    data object EmailTaken : SignupRepositoryError
    data object InvalidName : SignupRepositoryError, UpdateProfileNameLogicalError
    data class LocalOperationFailed(val error: LocalStorageError) : SignupRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : SignupRepositoryError
}

sealed interface UpdateProfileNameLogicalError {
    /**
     * Name length does not meet requirements.
     *
     * @property length Actual length of the provided name
     * @property min Minimum allowed length
     * @property max Maximum allowed length
     */
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        UpdateProfileNameLogicalError

    /**
     * Name contains characters that are not allowed.
     *
     * @property usedForbiddenCharacters Forbidden characters found in the name
     * @property forbiddenCharacters Complete list of forbidden characters
     */
    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateProfileNameLogicalError

    /**
     * Name violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : UpdateProfileNameLogicalError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownRuleViolation(val reason: String) : UpdateProfileNameLogicalError
}
