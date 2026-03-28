package timur.gilfanov.messenger.auth.data.source.remote.dto

import kotlinx.serialization.Serializable
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

@Serializable
data class CredentialsLoginRequestDto(val email: String, val password: String)

@Serializable
data class GoogleLoginRequestDto(val idToken: String)

@Serializable
data class GoogleSignupRequestDto(val idToken: String, val name: String)

@Serializable
data class RegisterRequestDto(val email: String, val password: String, val name: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthTokensDto(val accessToken: String, val refreshToken: String) {
    fun toDomain() = AuthTokens(accessToken, refreshToken)
}
