package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.collections.immutable.ImmutableList

/**
 * Errors specific to name update operations.
 *
 * Defines validation errors that can occur when updating a user's display name,
 * in addition to common errors from [UserOperationError].
 *
 * ## Validation Errors
 * - [LengthOutOfBounds] - Name too short or too long
 * - [ForbiddenCharacter] - Name contains prohibited characters
 * - [PlatformPolicyViolation] - Name violates content policy
 *
 * ## Inherited Errors
 * - Network/Service errors ([UserOperationError.ServiceUnavailable])
 * - Rate limiting ([UserOperationError.RateLimitExceeded])
 * - Cooldown restrictions ([UserOperationError.CooldownActive])
 * - Authentication errors ([UserOperationError.Unauthorized])
 */
sealed interface UpdateNameError : UserOperationError {
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
}
