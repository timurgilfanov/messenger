package timur.gilfanov.messenger.domain.entity.user

/**
 * Supported UI languages for the application.
 *
 * Represents available language options that users can select.
 * Changing the language affects all UI text and system messages.
 */
sealed interface UiLanguage : Setting {
    /** English language (en) */
    data object English : UiLanguage

    /** German language (de) */
    data object German : UiLanguage
}
