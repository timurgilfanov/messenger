package timur.gilfanov.messenger.domain

import java.security.MessageDigest
import timur.gilfanov.messenger.domain.entity.auth.AuthSession

@JvmInline
value class UserScopeKey(val key: String)

fun AuthSession.toUserScopeKey(): UserScopeKey = UserScopeKey(tokens.refreshToken.sha256Hex())

private fun String.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
