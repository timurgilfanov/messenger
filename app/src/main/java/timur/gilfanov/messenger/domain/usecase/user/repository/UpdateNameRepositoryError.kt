package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.collections.immutable.ImmutableList

sealed interface UpdateNameRepositoryError : UserRepositoryError {
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        UpdateNameRepositoryError

    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameRepositoryError

    sealed interface PlatformPolicyViolation : UpdateNameRepositoryError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    data class UnknownError(val reason: String) : UpdateNameRepositoryError
}
