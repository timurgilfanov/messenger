package timur.gilfanov.messenger.profile.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import timur.gilfanov.messenger.profile.R
import timur.gilfanov.messenger.ui.theme.MessengerTheme

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
                    is ProfileSideEffects.Feedback.ObserveFailed -> currentOnShowSnackbar(
                        getProfileErrorMessage,
                    )
                }
            }
        }
    }

    ProfileContent(
        uiState = profileUiState,
        onProfileEditClick = onProfileEditClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    @Suppress("unused") onProfileEditClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(200.dp)
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        when (uiState) {
            is ProfileUiState.Ready -> ProfileReadyContent(
                profile = uiState.profile,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("profile_ready"),
            )

            ProfileUiState.Loading -> Text(
                text = stringResource(R.string.settings_profile_loading_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("profile_loading"),
            )

            ProfileUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("profile_error"),
            ) {
                Text(
                    text = stringResource(R.string.settings_profile_error_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.settings_profile_retry))
                }
            }
        }
    }
}

@Composable
private fun ProfileReadyContent(profile: ProfileUi, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                )
                .testTag("profile_avatar"),
        ) {
            Text(
                text = profile.name.firstOrNull()?.uppercase() ?: "",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("profile_name"),
        )
    }
}

@Preview
@Composable
private fun ProfileContentReadyPreview() {
    Content(
        darkTheme = false,
        state = ProfileUiState.Ready(
            ProfileUi(
                name = "Timur",
                picture = null,
            ),
        ),
    )
}

@Preview
@Composable
private fun ProfileContentReadyDarkPreview() {
    Content(
        darkTheme = true,
        state = ProfileUiState.Ready(
            ProfileUi(
                name = "Timur",
                picture = null,
            ),
        ),
    )
}

@Preview
@Composable
private fun ProfileContentLoadingPreview() {
    Content(darkTheme = false, state = ProfileUiState.Loading)
}

@Preview(locale = "de")
@Composable
private fun ProfileContentGermanPreview() {
    Content(
        darkTheme = false,
        state = ProfileUiState.Ready(
            ProfileUi(
                name = "Timur",
                picture = null,
            ),
        ),
    )
}

@Composable
private fun Content(darkTheme: Boolean, state: ProfileUiState) {
    MessengerTheme(darkTheme = darkTheme) {
        ProfileContent(
            uiState = state,
            onProfileEditClick = {},
            onRetry = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
