package timur.gilfanov.messenger.domain.usecase.auth.repository

import kotlinx.collections.immutable.ImmutableList

/**
 * Server-side profile name validation errors returned by auth operations.
 *
 * Used as a detail type within [SignupRepositoryError.InvalidName] to describe why the name
 * was rejected. These are display-only — use cases branch on [SignupRepositoryError.InvalidName],
 * not on the sub-type.
 */
sealed interface ProfileNameValidationError {
    /**
     * Name length does not meet requirements.
     *
     * @property length Actual length of the provided name
     * @property min Minimum allowed length
     * @property max Maximum allowed length
     */
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        ProfileNameValidationError

    /**
     * Name contains characters that are not allowed.
     *
     * @property usedForbiddenCharacters Forbidden characters found in the name
     * @property forbiddenCharacters Complete list of forbidden characters
     */
    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : ProfileNameValidationError

    /**
     * Name violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : ProfileNameValidationError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownRuleViolation(val reason: String) : ProfileNameValidationError
}
