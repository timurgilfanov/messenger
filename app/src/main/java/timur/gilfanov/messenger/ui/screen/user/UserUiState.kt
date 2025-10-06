package timur.gilfanov.messenger.ui.screen.user

/**
 * UI state for the user profile screen.
 *
 * Aggregates user's profile information and settings into a single state
 * for display in the main user profile view.
 *
 * @property profile User's profile data (name, picture)
 * @property settings User's preference settings (language)
 */
data class UserUiState(val profile: ProfileUi, val settings: SettingsUi)
