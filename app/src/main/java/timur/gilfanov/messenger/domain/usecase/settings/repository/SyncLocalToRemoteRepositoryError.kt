package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.profile.repository.RepositoryError

/**
 * Error taxonomy for the "sync local settings to remote" repository method.
 *
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
typealias SyncLocalToRemoteRepositoryError = RepositoryError
