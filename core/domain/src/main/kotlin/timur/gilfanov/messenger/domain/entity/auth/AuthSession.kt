package timur.gilfanov.messenger.domain.entity.auth

data class AuthSession(val tokens: AuthTokens, val provider: AuthProvider)
