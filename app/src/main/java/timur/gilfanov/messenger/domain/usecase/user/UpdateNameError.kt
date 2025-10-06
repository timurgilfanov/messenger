package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.collections.immutable.ImmutableList

sealed interface UpdateNameError : UserOperationError {
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) : UpdateNameError

    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameError

    sealed interface PlatformPolicyViolation : UpdateNameError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }
}
