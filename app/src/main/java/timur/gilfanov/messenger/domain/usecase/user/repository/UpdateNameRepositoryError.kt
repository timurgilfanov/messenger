package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.collections.immutable.ImmutableList

/**
 * Errors specific to name update repository operations.
 *
 * Defines validation errors returned by the repository layer when updating names.
 *
 * ## Validation Errors
 * - [LengthOutOfBounds] - Name too short or too long
 * - [ForbiddenCharacter] - Name contains prohibited characters
 * - [PlatformPolicyViolation] - Name violates content policy
 *
 * ## Common Errors
 * - [UserRepository] - Wraps user-related repository errors
 */
sealed interface UpdateNameRepositoryError {
    /**
     * Name length does not meet requirements.
     *
     * @property length Actual length of the provided name
     * @property min Minimum allowed length
     * @property max Maximum allowed length
     */
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        UpdateNameRepositoryError

    /**
     * Name contains characters that are not allowed.
     *
     * @property usedForbiddenCharacters Forbidden characters found in the name
     * @property forbiddenCharacters Complete list of forbidden characters
     */
    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameRepositoryError

    /**
     * Name violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : UpdateNameRepositoryError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    /**
     * Common user repository errors.
     *
     * @property error The underlying user repository error
     */
    data class UserRepository(val error: UserRepositoryError) : UpdateNameRepositoryError
}
