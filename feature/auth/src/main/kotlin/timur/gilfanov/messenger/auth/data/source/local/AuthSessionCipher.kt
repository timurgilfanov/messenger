package timur.gilfanov.messenger.auth.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError

interface AuthSessionCipher {
    fun encrypt(plaintext: String): ResultWithError<String, AuthSessionCipherError>
    fun decrypt(encoded: String): ResultWithError<String, AuthSessionCipherError>
}

sealed interface AuthSessionCipherError {
    data object AccessDenied : AuthSessionCipherError
    data object KeystoreUnavailable : AuthSessionCipherError
    data object DataCorrupted : AuthSessionCipherError
    data class Unexpected(val cause: Throwable) : AuthSessionCipherError
}
