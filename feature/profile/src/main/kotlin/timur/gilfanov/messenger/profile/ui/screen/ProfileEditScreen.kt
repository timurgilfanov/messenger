package timur.gilfanov.messenger.profile.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Suppress("unused")
@Composable
fun ProfileEditScreen(
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) = Unit
