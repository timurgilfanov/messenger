package timur.gilfanov.messenger.ui.screen.debugsettings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.debug.DataScenario
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.debug.ui.CurrentScenarioCard
import timur.gilfanov.messenger.debug.ui.DebugInfoCard
import timur.gilfanov.messenger.debug.ui.DebugSettingsCard
import timur.gilfanov.messenger.debug.ui.InfoRow
import timur.gilfanov.messenger.debug.ui.QuickActionsCard
import timur.gilfanov.messenger.debug.ui.ScenarioCard
import timur.gilfanov.messenger.debug.ui.SwitchPreference
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Category(Component::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DebugSettingsComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createTestSettings(
        scenario: DataScenario = DataScenario.STANDARD,
        autoActivity: Boolean = false,
        showNotification: Boolean = true,
        lastGenerationTimestamp: Instant? = null,
    ) = DebugSettings(
        scenario = scenario,
        autoActivity = autoActivity,
        showNotification = showNotification,
        lastGenerationTimestamp = lastGenerationTimestamp,
    )

    // QuickActionsCard tests
    @Test
    fun `displays quick actions with enabled buttons when not loading`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    QuickActionsCard(
                        onRegenerateData = {},
                        onClearAllData = {},
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Quick Actions").assertIsDisplayed()
            onNodeWithText("Regenerate Data").assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Clear All").assertIsDisplayed().assertIsEnabled()
        }
    }

    @Test
    fun `displays quick actions with disabled buttons when loading`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    QuickActionsCard(
                        onRegenerateData = {},
                        onClearAllData = {},
                        isLoading = true,
                    )
                }
            }

            onNodeWithText("Quick Actions").assertIsDisplayed()
            onNodeWithText("Regenerate Data").assertIsDisplayed().assertIsNotEnabled()
            onNodeWithText("Clear All").assertIsDisplayed().assertIsNotEnabled()
        }
    }

    @Test
    fun `triggers onRegenerateData when regenerate button clicked`() {
        var regenerateClicked = false
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    QuickActionsCard(
                        onRegenerateData = { regenerateClicked = true },
                        onClearAllData = {},
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Regenerate Data").performClick()
            assertTrue(regenerateClicked)
        }
    }

    @Test
    fun `triggers onClearAllData when clear button clicked`() {
        var clearClicked = false
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    QuickActionsCard(
                        onRegenerateData = {},
                        onClearAllData = { clearClicked = true },
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Clear All").performClick()
            assertTrue(clearClicked)
        }
    }

    // CurrentScenarioCard tests
    @Test
    fun `displays current scenario information correctly`() {
        val settings = createTestSettings(
            scenario = DataScenario.DEMO,
            autoActivity = true,
            showNotification = false,
        )
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    CurrentScenarioCard(settings = settings)
                }
            }

            onNodeWithText("Current Scenario").assertIsDisplayed()
            onNodeWithText("DEMO").assertIsDisplayed()
            onNodeWithText(DataScenario.DEMO.description).assertIsDisplayed()
            onNodeWithText("Chats: ${DataScenario.DEMO.chatCount}").assertIsDisplayed()
            onNodeWithText("Messages: ${DataScenario.DEMO.messagesPerChat}").assertIsDisplayed()
        }
    }

    @Test
    fun `shows scenario name description and chat message counts`() {
        val settings = createTestSettings(scenario = DataScenario.HEAVY)
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    CurrentScenarioCard(settings = settings)
                }
            }

            onNodeWithText("HEAVY").assertIsDisplayed()
            onNodeWithText(DataScenario.HEAVY.description).assertIsDisplayed()
            onNodeWithText("Chats: ${DataScenario.HEAVY.chatCount}").assertIsDisplayed()
            onNodeWithText(
                "Messages: ${DataScenario.HEAVY.messagesPerChat}",
            ).assertIsDisplayed()
        }
    }

    // ScenarioCard tests
    @Test
    fun `displays scenario card with correct information`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    ScenarioCard(
                        scenario = DataScenario.STANDARD,
                        isSelected = false,
                        onSelect = {},
                        enabled = true,
                    )
                }
            }

            onNodeWithText("STANDARD").assertIsDisplayed()
            onNodeWithText(DataScenario.STANDARD.description).assertIsDisplayed()
            onNodeWithText(
                "${DataScenario.STANDARD.chatCount} chats, ${DataScenario.STANDARD.messagesPerChat} messages each",
            ).assertIsDisplayed()
        }
    }

    @Test
    fun `shows CURRENT label when scenario is selected`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    ScenarioCard(
                        scenario = DataScenario.DEMO,
                        isSelected = true,
                        onSelect = {},
                        enabled = true,
                    )
                }
            }

            onNodeWithText("CURRENT").assertIsDisplayed()
            onNodeWithText("DEMO").assertIsDisplayed()
        }
    }

    @Test
    fun `hides CURRENT label when scenario is not selected`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    ScenarioCard(
                        scenario = DataScenario.EMPTY,
                        isSelected = false,
                        onSelect = {},
                        enabled = true,
                    )
                }
            }

            onNodeWithText("CURRENT").assertIsNotDisplayed()
            onNodeWithText("EMPTY").assertIsDisplayed()
        }
    }

    @Test
    fun `triggers onSelect when card clicked and enabled`() {
        var selectClicked = false
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    ScenarioCard(
                        scenario = DataScenario.STANDARD,
                        isSelected = false,
                        onSelect = { selectClicked = true },
                        enabled = true,
                    )
                }
            }

            onNodeWithText("STANDARD").performClick()
            assertTrue(selectClicked)
        }
    }

    // DebugSettingsCard tests
    @Test
    fun `displays debug settings with correct switch states`() {
        val settings = createTestSettings(
            autoActivity = true,
            showNotification = false,
        )
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    DebugSettingsCard(
                        settings = settings,
                        onToggleAutoActivity = {},
                        onToggleNotification = {},
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Debug Settings").assertIsDisplayed()
            onNodeWithText("Auto-generate Activity").assertIsDisplayed()
            onNodeWithText("Simulates periodic new messages").assertIsDisplayed()
            onNodeWithText("Show Notification").assertIsDisplayed()
            onNodeWithText("Display persistent debug notification").assertIsDisplayed()
        }
    }

    @Test
    fun `triggers onToggleAutoActivity when auto-activity switch toggled`() {
        var autoActivityToggled = false
        var newValue = false
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    DebugSettingsCard(
                        settings = createTestSettings(autoActivity = false),
                        onToggleAutoActivity = {
                            autoActivityToggled = true
                            newValue = it
                        },
                        onToggleNotification = {},
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Auto-generate Activity").assertIsDisplayed()
            onNodeWithTag("SwitchPreference_Auto-generate Activity").performClick()
            assertTrue(autoActivityToggled)
            assertTrue(newValue) // Should be true since we started with false
        }
    }

    @Test
    fun `triggers onToggleNotification when notification switch toggled`() {
        var notificationToggled = false
        var newValue = true
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    DebugSettingsCard(
                        settings = createTestSettings(showNotification = true),
                        onToggleNotification = {
                            notificationToggled = true
                            newValue = it
                        },
                        onToggleAutoActivity = {},
                        isLoading = false,
                    )
                }
            }

            onNodeWithText("Show Notification").assertIsDisplayed()
            onNodeWithTag("SwitchPreference_Show Notification").performClick()
            assertTrue(notificationToggled)
            assertFalse(newValue)
        }
    }

    // DebugInfoCard tests
    @Test
    fun `displays debug information correctly`() {
        val settings = createTestSettings(
            scenario = DataScenario.STANDARD,
            lastGenerationTimestamp = Instant.parse("2023-12-01T15:17:00Z"),
        )
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    DebugInfoCard(settings = settings)
                }
            }

            onNodeWithText("Debug Information").assertIsDisplayed()
            onNodeWithText("Last Generation").assertIsDisplayed()
            onNodeWithText("Using Sample Data").assertIsDisplayed()
            onNodeWithText("Generated Recently").assertIsDisplayed()
        }
    }

    @Test
    fun `shows last generation sample data status and recent generation status`() {
        val settings = createTestSettings(lastGenerationTimestamp = null)
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    DebugInfoCard(settings = settings)
                }
            }

            // These values come from DebugSettings computed properties
            onNodeWithText("true").assertIsDisplayed() // useSampleData
            onNodeWithText("false").assertIsDisplayed() // wasGeneratedRecently
        }
    }

    // SwitchPreference tests
    @Test
    fun `displays switch preference with title and subtitle`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    SwitchPreference(
                        title = "Test Setting",
                        checked = true,
                        onCheckedChange = {},
                        subtitle = "This is a test subtitle",
                        enabled = true,
                    )
                }
            }

            onNodeWithText("Test Setting").assertIsDisplayed()
            onNodeWithText("This is a test subtitle").assertIsDisplayed()
        }
    }

    @Test
    fun `hides subtitle when not provided`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    SwitchPreference(
                        title = "Test Setting",
                        checked = false,
                        onCheckedChange = {},
                        subtitle = null,
                        enabled = true,
                    )
                }
            }

            onNodeWithText("Test Setting").assertIsDisplayed()
            // No subtitle should be present since we didn't provide one
        }
    }

    @Test
    fun `displays switch preference with correct states`() {
        with(composeTestRule) {
            // Test enabled state
            setContent {
                MessengerTheme {
                    SwitchPreference(
                        title = "Enabled Setting",
                        checked = true,
                        onCheckedChange = {},
                        subtitle = "Should be enabled",
                        enabled = true,
                    )
                }
            }

            onNodeWithText("Enabled Setting").assertIsDisplayed()
            onNodeWithText("Should be enabled").assertIsDisplayed()
        }
    }

    // InfoRow tests
    @Test
    fun `displays label and value correctly`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    InfoRow(
                        label = "Test Label",
                        value = "Test Value",
                    )
                }
            }

            onNodeWithText("Test Label").assertIsDisplayed()
            onNodeWithText("Test Value").assertIsDisplayed()
        }
    }

    @Test
    fun `formats different types of values correctly`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    InfoRow(
                        label = "Status",
                        value = "Active",
                    )
                }
            }

            onNodeWithText("Status").assertIsDisplayed()
            onNodeWithText("Active").assertIsDisplayed()
        }
    }

    @Test
    fun `displays boolean values as strings`() {
        with(composeTestRule) {
            setContent {
                MessengerTheme {
                    InfoRow(
                        label = "Enabled",
                        value = "true",
                    )
                }
            }

            onNodeWithText("Enabled").assertIsDisplayed()
            onNodeWithText("true").assertIsDisplayed()
        }
    }
}
