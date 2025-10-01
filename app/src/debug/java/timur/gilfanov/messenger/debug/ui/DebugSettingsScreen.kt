package timur.gilfanov.messenger.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.debug.DataScenario
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun DebugSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.collectAsState()

    DebugSettingsContent(
        settings = state.settings,
        isLoading = state.isLoading,
        actions = DebugSettingsActions(
            onRegenerateData = { viewModel.regenerateData() },
            onClearAllData = { viewModel.clearAllData() },
            onSwitchScenario = { scenario -> viewModel.switchScenario(scenario) },
            onToggleAutoActivity = { enabled -> viewModel.toggleAutoActivity(enabled) },
            onToggleNotification = { enabled -> viewModel.toggleNotification(enabled) },
        ),
        modifier = modifier,
    )
}

@Composable
fun DebugSettingsContent(
    settings: DebugSettings,
    isLoading: Boolean,
    actions: DebugSettingsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QuickActionsCard(
                onRegenerateData = actions.onRegenerateData,
                onClearAllData = actions.onClearAllData,
                isLoading = isLoading,
            )
        }

        item {
            CurrentScenarioCard(settings = settings)
        }

        item {
            Text(
                text = "Available Scenarios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        items(DataScenario.entries) { scenario ->
            ScenarioCard(
                scenario = scenario,
                isSelected = scenario == settings.scenario,
                onSelect = { actions.onSwitchScenario(scenario) },
                enabled = !isLoading,
            )
        }

        item {
            DebugSettingsCard(
                settings = settings,
                onToggleAutoActivity = actions.onToggleAutoActivity,
                onToggleNotification = actions.onToggleNotification,
                isLoading = isLoading,
            )
        }

        item {
            DebugInfoCard(settings = settings)
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DebugSettingsContentPreview() {
    MessengerTheme {
        DebugSettingsContent(
            settings = DebugSettings(
                scenario = DataScenario.STANDARD,
                autoActivity = true,
                showNotification = true,
            ),
            isLoading = false,
            actions = NoOpDebugSettingsActions,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DebugSettingsContentLoadingPreview() {
    MessengerTheme {
        DebugSettingsContent(
            settings = DebugSettings(
                scenario = DataScenario.HEAVY,
                autoActivity = false,
                showNotification = false,
            ),
            isLoading = true,
            actions = NoOpDebugSettingsActions,
        )
    }
}

data class DebugSettingsActions(
    val onRegenerateData: () -> Unit,
    val onClearAllData: () -> Unit,
    val onSwitchScenario: (DataScenario) -> Unit,
    val onToggleAutoActivity: (Boolean) -> Unit,
    val onToggleNotification: (Boolean) -> Unit,
)

val NoOpDebugSettingsActions = DebugSettingsActions(
    onRegenerateData = {},
    onClearAllData = {},
    onSwitchScenario = {},
    onToggleAutoActivity = {},
    onToggleNotification = {},
)
