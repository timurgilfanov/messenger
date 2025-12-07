package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Error type for settings observation operations at the repository layer.
 *
 * Aliased to [GetSettingsRepositoryError] as observation uses the same error cases
 * as direct retrieval. This type alias provides semantic clarity at call sites
 * and allows for future differentiation if observation-specific errors are needed.
 */
typealias ObserveSettingsRepositoryError = GetSettingsRepositoryError
