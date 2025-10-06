package timur.gilfanov.messenger.domain.entity.user

/**
 * User application settings.
 *
 * Contains user preferences and configuration options for the application.
 * These settings are stored remotely and synced across devices.
 *
 * @property language UI language preference for the application
 */
data class Settings(val language: UiLanguage)
