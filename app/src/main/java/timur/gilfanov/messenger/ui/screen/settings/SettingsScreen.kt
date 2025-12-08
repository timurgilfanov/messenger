@file:Suppress("unused")

package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
@Suppress("LongParameterList")
fun SettingsScreen(
    onProfileEditClick: () -> Unit,
    onChangeLanguageClick: () -> Unit,
    onAuthFailure: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val profileUiState by profileViewModel.collectAsState()
    val settingsUiState by settingsViewModel.collectAsState()

    val getSettingsErrorMessage = stringResource(R.string.settings_get_settings_failed)

    settingsViewModel.collectSideEffect {
        when (it) {
            is SettingsSideEffects.GetSettingsFailed -> onShowSnackbar(getSettingsErrorMessage)
            SettingsSideEffects.Unauthorized -> onAuthFailure()
        }
    }
    SettingsScreenContent(
        profileUiState = profileUiState,
        settingsUiState = settingsUiState,
        onProfileEditClick = onProfileEditClick,
        onChangeLanguageClick = onChangeLanguageClick,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreenContent(
    profileUiState: ProfileUiState,
    settingsUiState: SettingsUiState,
    onProfileEditClick: () -> Unit,
    onChangeLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ProfileContent(
            uiState = profileUiState,
            onProfileEditClick = onProfileEditClick,
        )
        SettingsContent(
            uiState = settingsUiState,
            onChangeLanguageClick = onChangeLanguageClick,
        )
    }
}

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onProfileEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onChangeLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is SettingsUiState.Loading -> {
            Box(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is SettingsUiState.Ready -> {
            Column {
                SettingsListItem(
                    title = stringResource(R.string.settings_language_screen_title),
                    value = uiState.settings.language.toSettingsItem(),
                    action = onChangeLanguageClick,
                )
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    title: String,
    value: String,
    action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(enabled = true, onClick = action)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun UiLanguage.toSettingsItem(): String = stringResource(
    when (this) {
        UiLanguage.English -> R.string.settings_language_english
        UiLanguage.German -> R.string.settings_language_german
    },
)

@Preview
@Composable
private fun SettingsScreenContentLightPreview() {
    Content(darkTheme = false)
}

@Preview
@Composable
private fun SettingsScreenContentDarkPreview() {
    Content(darkTheme = true)
}

@Preview(locale = "de")
@Composable
private fun SettingsScreenContentGermanPreview() {
    Content(darkTheme = false)
}

@Composable
private fun Content(
    darkTheme: Boolean,
    profileUiState: ProfileUiState = ProfileUiState.Ready(
        ProfileUi(
            name = "Timur",
            picture = null,
        ),
    ),
    settingsUiState: SettingsUiState = SettingsUiState.Ready(
        SettingsUi(
            language = UiLanguage.English,
        ),
    ),
) {
    MessengerTheme(darkTheme = darkTheme) {
        SettingsScreenContent(
            profileUiState = profileUiState,
            settingsUiState = settingsUiState,
            onProfileEditClick = {},
            onChangeLanguageClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Preview
@Composable
private fun SettingsScreenContentLoadingPreview() {
    Content(
        darkTheme = false,
        profileUiState = ProfileUiState.Loading,
        settingsUiState = SettingsUiState.Loading,
    )
}

@Preview
@Composable
private fun SettingsScreenContentProfileLoadingPreview() {
    Content(
        darkTheme = false,
        profileUiState = ProfileUiState.Loading,
    )
}

@Preview
@Composable
private fun SettingsScreenContentSettingsLoadingPreview() {
    Content(
        darkTheme = false,
        settingsUiState = SettingsUiState.Loading,
    )
}
