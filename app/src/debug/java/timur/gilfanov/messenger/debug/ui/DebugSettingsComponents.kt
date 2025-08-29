package timur.gilfanov.messenger.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import timur.gilfanov.messenger.debug.DataScenario
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.ui.theme.MessengerTheme

private const val DISABLED_ALPHA = 0.6f

@Composable
internal fun QuickActionsCard(
    onRegenerateData: () -> Unit,
    onClearAllData: () -> Unit,
    isLoading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRegenerateData,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Regenerate Data")
                }

                OutlinedButton(
                    onClick = onClearAllData,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear All")
                }
            }
        }
    }
}

@Composable
internal fun CurrentScenarioCard(settings: DebugSettings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Current Scenario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = settings.scenario.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = settings.scenario.description,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Chats: ${settings.effectiveChatCount}")
                Text("Messages: ${settings.effectiveMessageRange}")
            }
        }
    }
}

@Composable
internal fun ScenarioCard(
    scenario: DataScenario,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(DISABLED_ALPHA) else Modifier),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        onClick = onSelect,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )

                if (isSelected) {
                    Text(
                        text = "CURRENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "${scenario.chatCount} chats, ${scenario.messagesPerChat} messages each",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DebugSettingsCard(
    settings: DebugSettings,
    onToggleAutoActivity: (Boolean) -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    isLoading: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Debug Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            SwitchPreference(
                title = "Auto-generate Activity",
                checked = settings.autoActivity,
                onCheckedChange = onToggleAutoActivity,
                subtitle = "Simulates periodic new messages",
                enabled = !isLoading,
            )

            SwitchPreference(
                title = "Show Notification",
                checked = settings.showNotification,
                onCheckedChange = onToggleNotification,
                subtitle = "Display persistent debug notification",
                enabled = !isLoading,
            )
        }
    }
}

@Composable
internal fun DebugInfoCard(settings: DebugSettings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Debug Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            InfoRow("Last Generation", settings.lastGenerationFormatted)
            InfoRow("Using Sample Data", settings.useSampleData.toString())
            InfoRow("Generated Recently", settings.wasGeneratedRecently.toString())
        }
    }
}

@Composable
internal fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
private fun QuickActionsCardPreview() {
    MessengerTheme {
        QuickActionsCard(
            onRegenerateData = {},
            onClearAllData = {},
            isLoading = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickActionsCardLoadingPreview() {
    MessengerTheme {
        QuickActionsCard(
            onRegenerateData = {},
            onClearAllData = {},
            isLoading = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentScenarioCardPreview() {
    MessengerTheme {
        CurrentScenarioCard(
            settings = DebugSettings(
                scenario = DataScenario.STANDARD,
                autoActivity = true,
                showNotification = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScenarioCardPreview() {
    MessengerTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScenarioCard(
                scenario = DataScenario.STANDARD,
                isSelected = true,
                onSelect = {},
                enabled = true,
            )
            ScenarioCard(
                scenario = DataScenario.HEAVY,
                isSelected = false,
                onSelect = {},
                enabled = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugSettingsCardPreview() {
    MessengerTheme {
        DebugSettingsCard(
            settings = DebugSettings(
                scenario = DataScenario.STANDARD,
                autoActivity = true,
                showNotification = false,
            ),
            onToggleAutoActivity = {},
            onToggleNotification = {},
            isLoading = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugInfoCardPreview() {
    MessengerTheme {
        DebugInfoCard(
            settings = DebugSettings(
                scenario = DataScenario.STANDARD,
                autoActivity = true,
                showNotification = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SwitchPreferencePreview() {
    MessengerTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SwitchPreference(
                title = "Auto-generate Activity",
                checked = true,
                onCheckedChange = {},
                subtitle = "Simulates periodic new messages",
                enabled = true,
            )
            SwitchPreference(
                title = "Show Notification",
                checked = false,
                onCheckedChange = {},
                subtitle = "Display persistent debug notification",
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoRowPreview() {
    MessengerTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Last Generation", "2 minutes ago")
            InfoRow("Using Sample Data", "true")
            InfoRow("Generated Recently", "false")
        }
    }
}
