package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList

sealed interface UpdateNameRemoteDataSourceError : RemoteUserDataSourceError {
    data class LengthOutOfBounds(val length: Int, val min: Int, val max: Int) :
        UpdateNameRemoteDataSourceError

    data class ForbiddenCharacter(
        val usedForbiddenCharacters: ImmutableList<Char>,
        val forbiddenCharacters: ImmutableList<Char>,
    ) : UpdateNameRemoteDataSourceError

    sealed interface PlatformPolicyViolation : UpdateNameRemoteDataSourceError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }
}
