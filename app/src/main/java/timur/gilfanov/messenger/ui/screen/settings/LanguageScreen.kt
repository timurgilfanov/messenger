package timur.gilfanov.messenger.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.entity.settings.uiLanguageList
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun LanguageScreen(
    onAuthFailure: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorMessage = stringResource(R.string.settings_language_change_failed)

    val onShowSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    viewModel.collectSideEffect {
        when (it) {
            is LanguageSideEffects.ChangeFailed -> onShowSnackbar(errorMessage)
            LanguageSideEffects.Unauthorized -> onAuthFailure()
        }
    }

    LanguageScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSelectLanguage = viewModel::changeLanguage,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreenContent(
    uiState: LanguageUiState,
    snackbarHostState: SnackbarHostState,
    onSelectLanguage: (UiLanguage) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_language_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("language_back_button"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.content_description_navigate_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
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
            snackbarHostState = remember { SnackbarHostState() },
            onSelectLanguage = {},
            onBackClick = {},
        )
    }
}
