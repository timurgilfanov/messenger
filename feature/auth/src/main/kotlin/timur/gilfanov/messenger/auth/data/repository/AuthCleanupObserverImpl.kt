package timur.gilfanov.messenger.auth.data.repository

import dagger.Lazy
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.di.ApplicationScope
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Singleton
class AuthCleanupObserverImpl @Inject constructor(
    private val authRepository: Lazy<AuthRepository>,
    private val settingsRepository: Lazy<SettingsRepository>,
    @ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger,
) : AuthCleanupObserver {
    companion object {
        private const val TAG = "AuthCleanupObserver"
    }

    private val isStarted = AtomicBoolean(false)

    override fun start() {
        if (!isStarted.compareAndSet(false, true)) return

        scope.launch {
            var previousState: AuthState? = null
            authRepository.get().authState.collect { currentState ->
                val prev = previousState
                if (prev != null) handleTransition(prev, currentState)
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

    private suspend fun deleteUserData(userKey: UserScopeKey) {
        val result = settingsRepository.get().deleteUserData(userKey)
        if (result is ResultWithError.Failure) {
            logger.e(TAG, "Failed to clean up user-scoped settings: ${result.error}")
        }
    }
}
