package timur.gilfanov.messenger.domain.entity.auth

/**
 * Authentication tokens issued after a successful auth operation.
 *
 * @property accessToken Short-lived token used to authorize API requests.
 * @property refreshToken Long-lived token used to obtain a new [accessToken] when it expires.
 */
data class AuthTokens(val accessToken: String, val refreshToken: String)
