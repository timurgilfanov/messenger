@file:Suppress("unused")

package timur.gilfanov.messenger.ui.screen.user

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun UserScreen(
    onProfileEditClick: () -> Unit,
    onChangeLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) = Unit
