package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.uiLanguageList
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun LanguageScreen(
    onAuthFailure: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.collectAsState()
    val errorMessage = stringResource(R.string.settings_language_change_failed)

    viewModel.collectSideEffect {
        when (it) {
            is LanguageSideEffects.ChangeFailed -> onShowSnackbar(errorMessage)
            LanguageSideEffects.Unauthorized -> onAuthFailure()
        }
    }

    LanguageScreenContent(
        uiState = uiState,
        onSelectLanguage = viewModel::changeLanguage,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreenContent(
    uiState: LanguageUiState,
    onSelectLanguage: (UiLanguage) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        MediumTopAppBar(
            title = { Text(stringResource(R.string.settings_language_screen_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.content_description_navigate_back),
                    )
                }
            },
        )
        uiState.languages.forEach { language ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = language == uiState.selectedLanguage,
                    onClick = { onSelectLanguage(language) },
                    modifier = Modifier.testTag("language_radio_$language"),
                )
                Text(
                    text = language.toListItemTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .testTag("language_text_$language")
                        .clickable(enabled = true) { onSelectLanguage(language) },
                )
            }
        }
    }
}

@Composable
private fun UiLanguage.toListItemTitle(): String = stringResource(
    when (this) {
        UiLanguage.English -> R.string.settings_language_english
        UiLanguage.German -> R.string.settings_language_german
    },
)

@Preview
@Composable
private fun LanguageScreenContentLightPreview() {
    Content(darkTheme = false)
}

@Preview
@Composable
private fun LanguageScreenContentDarkPreview() {
    Content(darkTheme = true)
}

@Preview(locale = "de")
@Composable
private fun LanguageScreenContentGermanPreview() {
    Content(darkTheme = false)
}

@Composable
private fun Content(darkTheme: Boolean) {
    val uiState = LanguageUiState(
        languages = uiLanguageList,
        selectedLanguage = UiLanguage.English,
    )

    MessengerTheme(darkTheme = darkTheme) {
        LanguageScreenContent(
            uiState = uiState,
            onSelectLanguage = {},
            onBackClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
