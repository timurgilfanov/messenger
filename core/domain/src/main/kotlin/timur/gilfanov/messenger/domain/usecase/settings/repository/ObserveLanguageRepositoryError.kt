package timur.gilfanov.messenger.domain.usecase.settings.repository

/**
 * Errors that can occur when observing language preferences from the repository.
 *
 * This is an alias for [GetSettingsRepositoryError] since language observation
 * is a subset of settings retrieval operations.
 */
typealias ObserveLanguageRepositoryError = GetSettingsRepositoryError
