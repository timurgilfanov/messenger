package timur.gilfanov.messenger.auth.data.repository

import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

/**
 * Reactively cleans up user-scoped data when the user logs out or session expires.
 *
 * Observes [AuthRepository.authState] and triggers [SettingsRepository.deleteUserData]
 * on Authenticated -> Unauthenticated transitions or when the user scope key changes
 * (e.g. during token rotation if it affects the key).
 */
interface AuthCleanupObserver {
    /**
     * Starts observing auth state transitions.
     * Should be called once during application initialization. Subsequent calls are no-ops.
     */
    fun start()
}
