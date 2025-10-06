package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList

/**
 * Errors specific to name update remote operations.
 *
 * Defines validation errors returned by the backend when updating names.
 *
 * ## Validation Errors
 * - [LengthOutOfBounds] - Name too short or too long
 * - [ForbiddenCharacter] - Name contains prohibited characters
 * - [PlatformPolicyViolation] - Name violates content policy
 *
 * ## Inherited Errors
 * Inherits all errors from [RemoteUserDataSourceError] and [RemoteDataSourceErrorV2].
 */
sealed interface UpdateNameRemoteDataSourceError : RemoteUserDataSourceError {
    /**
     * Name length does not meet requirements.
     *
     * @property length Actual length of the provided name
     * @property min Minimum allowed length
     * @property max Maximum allowed length
     */
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        UpdateNameRemoteDataSourceError

    /**
     * Name contains characters that are not allowed.
     *
     * @property usedForbiddenCharacters Forbidden characters found in the name
     * @property forbiddenCharacters Complete list of forbidden characters
     */
    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameRemoteDataSourceError

    /**
     * Name violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : UpdateNameRemoteDataSourceError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }
}
