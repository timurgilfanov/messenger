package timur.gilfanov.messenger.domain.entity.auth

/**
 * @property sessionId Stable identifier for the logical login session. Generated at login/signup
 *   and preserved across token rotations so that user-scoped data partitions remain stable when
 *   the refresh token is rotated.
 */
data class AuthSession(val tokens: AuthTokens, val provider: AuthProvider, val sessionId: String)
