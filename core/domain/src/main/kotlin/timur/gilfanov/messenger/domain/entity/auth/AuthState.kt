package timur.gilfanov.messenger.domain.entity.auth

sealed interface AuthState {
    data class Authenticated(val session: AuthSession) : AuthState
    data object Unauthenticated : AuthState
}
