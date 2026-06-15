package timur.gilfanov.messenger.auth.data.repository

import dagger.Lazy
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.auth.di.ApplicationScope
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.UserScopedDataCleaner
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.util.Logger

@Singleton
class AuthCleanupObserverImpl @Inject constructor(
    private val authRepository: Lazy<AuthRepository>,
    private val dataCleaners: Lazy<Set<@JvmSuppressWildcards UserScopedDataCleaner>>,
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
        if (previous !is AuthState.Authenticated) return
        val previousKey = previous.session.toUserScopeKey()
        val shouldClean = when (current) {
            is AuthState.Unauthenticated -> {
                logger.d(
                    TAG,
                    "Auth transition: Authenticated -> Unauthenticated. Cleaning up data.",
                )
                true
            }
            is AuthState.Authenticated -> {
                val changed = previousKey != current.session.toUserScopeKey()
                if (changed) {
                    logger.d(TAG, "Auth transition: User scope changed. Cleaning up old data.")
                }
                changed
            }
        }
        if (shouldClean) {
            deleteUserData(previousKey).onFailure { errors ->
                errors.forEach { error ->
                    logger.e(TAG, "Failed to clean up user-scoped data: $error")
                }
            }
        }
    }

    private suspend fun deleteUserData(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, List<LocalStorageError>> {
        val failures = buildList {
            dataCleaners.get().forEach { cleaner ->
                cleaner.deleteUserData(userKey).onFailure { add(it) }
            }
        }
        return if (failures.isEmpty()) {
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(failures)
        }
    }
}
