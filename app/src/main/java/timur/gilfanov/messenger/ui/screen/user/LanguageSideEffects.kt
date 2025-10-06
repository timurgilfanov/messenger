package timur.gilfanov.messenger.ui.screen.user

/**
 * One-time side effects for the language selection screen.
 *
 * Represents events that should be handled once and not persist across
 * configuration changes, such as showing snackbars or toasts.
 */
sealed interface LanguageSideEffects {
    /**
     * Language change partially failed.
     *
     * Indicates the language was changed on this device but could not be
     * synchronized to all other devices. The user should be informed via
     * a transient message (e.g., toast or snackbar).
     *
     * @property reason Human-readable description of why synchronization failed
     */
    data class LanguageNotChangedForAllDevices(val reason: String) : LanguageSideEffects
}
