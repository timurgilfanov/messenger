package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.profile.repository.RepositoryError
/**
 * Error taxonomy for the "change settings backup" repository method.
 *
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
typealias SettingsChangeBackupError = RepositoryError
