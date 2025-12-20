package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError

/**
 * One-time side effects for the language selection screen.
 *
 * Represents events that should be handled once and not persist across
 * configuration changes, such as showing snackbars or toasts.
 */
sealed interface LanguageSideEffects {

    data object Unauthorized : LanguageSideEffects

    data class ChangeFailed(val error: ChangeLanguageRepositoryError) : LanguageSideEffects
}
