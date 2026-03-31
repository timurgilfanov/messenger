package timur.gilfanov.messenger.domain.entity.auth

import timur.gilfanov.messenger.domain.entity.profile.UserId

data class AuthSession(val tokens: AuthTokens, val provider: AuthProvider, val userId: UserId)
