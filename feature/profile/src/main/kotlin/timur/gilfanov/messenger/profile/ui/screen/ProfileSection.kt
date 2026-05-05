package timur.gilfanov.messenger.profile.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import timur.gilfanov.messenger.profile.R

@Composable
fun ProfileSection(
    onProfileEditClick: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val currentOnShowSnackbar by rememberUpdatedState(onShowSnackbar)
    val lifecycleOwner = LocalLifecycleOwner.current
    val profileUiState by viewModel.state.collectAsStateWithLifecycle()
    val getProfileErrorMessage = stringResource(R.string.settings_get_profile_failed)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
            viewModel.effects.collect { effects ->
                when (effects) {
                    is ProfileSideEffects.ObserveProfileFailed -> currentOnShowSnackbar(
                        getProfileErrorMessage,
                    )
                }
            }
        }
    }

    ProfileContent(
        uiState = profileUiState,
        onProfileEditClick = onProfileEditClick,
        modifier = modifier,
    )
}

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    @Suppress("unused") onProfileEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(200.dp)
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        val text = when (uiState) {
            is ProfileUiState.Ready -> R.string.settings_profile_ready_placeholder
            ProfileUiState.Loading -> R.string.settings_profile_loading_placeholder
        }
        val testTag = when (uiState) {
            is ProfileUiState.Ready -> "profile_ready"
            ProfileUiState.Loading -> "profile_loading"
        }
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .testTag(testTag),
        )
    }
}
