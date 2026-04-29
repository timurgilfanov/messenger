package timur.gilfanov.messenger.auth.data.repository

import dagger.Lazy
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.di.ApplicationScope
import timur.gilfanov.messenger.domain.UserScopeKey
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
 *
 * Also recovers from process death: if the process was killed after the session was cleared
 * but before cleanup completed, the pending cleanup key written by [AuthRepository.logout]
 * is consumed on the next startup.
 */
@Singleton
class AuthCleanupObserver @Inject constructor(
    private val authRepository: Lazy<AuthRepository>,
    private val settingsRepository: Lazy<SettingsRepository>,
    private val localAuthDataSource: Lazy<LocalAuthDataSource>,
    @ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "AuthCleanupObserver"
    }

    private val isStarted = AtomicBoolean(false)

    /**
     * Starts observing auth state transitions.
     * Should be called once during application initialization.
     */
    fun start() {
        if (!isStarted.compareAndSet(false, true)) return

        scope.launch {
            var previousState: AuthState? = null
            authRepository.get().authState.collect { currentState ->
                val prev = previousState
                previousState = currentState

                if (prev == null) {
                    handleInitialState(currentState)
                } else {
                    handleTransition(prev, currentState)
                }
            }
        }
    }

    private suspend fun handleInitialState(currentState: AuthState) {
        when (val result = localAuthDataSource.get().getPendingCleanupKey()) {
            is ResultWithError.Success -> processPendingCleanupKey(result.data, currentState)
            is ResultWithError.Failure ->
                logger.e(TAG, "Failed to read pending cleanup key: ${result.error}")
        }
    }

    private suspend fun processPendingCleanupKey(key: UserScopeKey?, currentState: AuthState) {
        if (key == null) return
        val needsCleanup = when (currentState) {
            is AuthState.Unauthenticated -> true
            is AuthState.Authenticated -> currentState.session.toUserScopeKey() != key
        }
        if (needsCleanup) {
            logger.d(TAG, "Found pending cleanup from previous session, processing...")
            val cleanupSucceeded = deleteUserData(key)
            if (cleanupSucceeded) {
                clearSavedPendingCleanupKeyIfMatches(key)
            }
        } else {
            clearSavedPendingCleanupKeyIfMatches(key)
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
                    val cleanupSucceeded = deleteUserData(previousKey)
                    if (cleanupSucceeded) {
                        clearSavedPendingCleanupKeyIfMatches(previousKey)
                    }
                }
                is AuthState.Authenticated -> {
                    val currentKey = current.session.toUserScopeKey()
                    if (previousKey != currentKey) {
                        handleScopeChange(previousKey)
                    }
                }
            }
        }
    }

    private suspend fun handleScopeChange(previousKey: UserScopeKey) {
        logger.d(TAG, "Auth transition: User scope changed. Cleaning up old data.")
        val markerResult = localAuthDataSource.get().setPendingCleanupKeyIfAbsent(previousKey)
        when (markerResult) {
            is ResultWithError.Success ->
                if (!markerResult.data) {
                    logger.d(
                        TAG,
                        "Another cleanup is already pending; running cleanup for $previousKey best-effort.",
                    )
                }
            is ResultWithError.Failure ->
                logger.e(
                    TAG,
                    "Failed to write pending cleanup marker for scope change: ${markerResult.error}",
                )
        }
        val cleanupSucceeded = deleteUserData(previousKey)
        if (cleanupSucceeded) {
            clearSavedPendingCleanupKeyIfMatches(previousKey)
        }
    }

    private suspend fun deleteUserData(userKey: UserScopeKey): Boolean {
        val result = settingsRepository.get().deleteUserData(userKey)
        return if (result is ResultWithError.Failure) {
            logger.e(TAG, "Failed to clean up settings for $userKey: ${result.error}")
            false
        } else {
            true
        }
    }

    private suspend fun clearSavedPendingCleanupKeyIfMatches(key: UserScopeKey) {
        val clearResult = localAuthDataSource.get().clearPendingCleanupKeyIfMatches(key)
        if (clearResult is ResultWithError.Failure) {
            logger.e(TAG, "Failed to clear pending cleanup key: ${clearResult.error}")
        }
    }
}
