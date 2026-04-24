package timur.gilfanov.messenger.auth.data.repository

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.di.ApplicationScope
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Reactively cleans up user-scoped data when the user logs out or session expires.
 *
 * Observes [AuthRepository.authState] and triggers [SettingsRepository.deleteUserData]
 * on Authenticated -> Unauthenticated transitions or when the user scope key changes
 * (e.g. during token rotation if it affects the key).
 */
@Singleton
class AuthCleanupObserver @Inject constructor(
    private val authRepository: Lazy<AuthRepository>,
    private val settingsRepository: Lazy<SettingsRepository>,
    @ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "AuthCleanupObserver"
    }

    private var isStarted = false

    /**
     * Starts observing auth state transitions.
     * Should be called once during application initialization.
     */
    fun start() {
        if (isStarted) return
        isStarted = true

        scope.launch {
            val repository = authRepository.get()
            var previousState: AuthState = repository.authState.first()
            repository.authState.collect { currentState ->
                handleTransition(previousState, currentState)
                previousState = currentState
            }
        }
    }

    private suspend fun handleTransition(previous: AuthState, current: AuthState) {
        if (previous is AuthState.Authenticated) {
            val previousKey = previous.session.toUserScopeKey()

            when (current) {
                is AuthState.Unauthenticated -> {
                    logger.d(
                        TAG,
                        "Auth transition: Authenticated -> Unauthenticated. Cleaning up data.",
                    )
                    deleteUserData(previousKey)
                }
                is AuthState.Authenticated -> {
                    val currentKey = current.session.toUserScopeKey()
                    if (previousKey != currentKey) {
                        logger.d(TAG, "Auth transition: User scope changed. Cleaning up old data.")
                        deleteUserData(previousKey)
                    }
                }
            }
        }
    }

    private suspend fun deleteUserData(userKey: timur.gilfanov.messenger.domain.UserScopeKey) {
        val result = settingsRepository.get().deleteUserData(userKey)
        if (result is ResultWithError.Failure) {
            logger.e(TAG, "Failed to clean up settings for $userKey: ${result.error}")
        }
    }
}
