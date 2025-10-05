package timur.gilfanov.messenger.ui.screen.user

data class UserUiState(
    val profile: ProfileUi,
    val profilePictureLarge: Boolean = false,
    val settings: SettingsUi,
)
