@file:Suppress("unused")

package timur.gilfanov.messenger.ui.screen.user

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UserScreen(
    onProfileEditClick: () -> Unit,
    onChangeLanguageClick: () -> Unit,
    viewModel: UserViewModel,
    modifier: Modifier = Modifier,
) = Unit
