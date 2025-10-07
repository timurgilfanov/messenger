package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.collections.immutable.ImmutableList
import timur.gilfanov.messenger.domain.usecase.user.repository.UpdateNameRepositoryError

/**
 * Errors specific to name update operations.
 *
 * ## Validation Errors
 * - [LengthOutOfBounds] - Name too short or too long
 * - [ForbiddenCharacter] - Name contains prohibited characters
 * - [PlatformPolicyViolation] - Name violates content policy
 *
 * ## Repository Errors
 * - [RepositoryError] - Wraps repository layer errors
 */
sealed interface UpdateNameError {
    /**
     * Name length does not meet requirements.
     *
     * @property length Actual length of the provided name
     * @property min Minimum allowed length
     * @property max Maximum allowed length
     */
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) : UpdateNameError

    /**
     * Name contains characters that are not allowed.
     *
     * @property usedForbiddenCharacters Forbidden characters found in the name
     * @property forbiddenCharacters Complete list of forbidden characters
     */
    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameError

    /**
     * Name violates platform content policy.
     *
     * These errors indicate that the name contains inappropriate content
     * detected by platform moderation systems.
     */
    sealed interface PlatformPolicyViolation : UpdateNameError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    /**
     * Repository layer errors.
     *
     * @property error The underlying repository error
     */
    data class RepositoryError(val error: UpdateNameRepositoryError) : UpdateNameError
}
