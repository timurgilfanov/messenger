package timur.gilfanov.messenger.domain

import timur.gilfanov.messenger.domain.entity.auth.AuthSession

@JvmInline
value class UserScopeKey(val key: String)

fun AuthSession.toUserScopeKey(): UserScopeKey = UserScopeKey(tokens.refreshToken)
