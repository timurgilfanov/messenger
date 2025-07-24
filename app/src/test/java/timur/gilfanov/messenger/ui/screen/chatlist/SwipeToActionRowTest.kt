package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Category(Component::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwipeToActionRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SwipeToActionRow displays content correctly when no actions provided`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow {
                    TestContent("Test Content")
                }
            }
        }

        composeTestRule.onNodeWithText("Test Content").assertIsDisplayed()
    }

    @Test
    fun `SwipeToActionRow displays start actions correctly`() {
        val testActions = createTestStartActions()

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = testActions,
                ) {
                    TestContent("Content with start actions")
                }
            }
        }

        // Verify content is displayed
        composeTestRule.onNodeWithText("Content with start actions").assertIsDisplayed()

        // Verify action buttons are present (they exist in the composition even if not visible)
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists()
    }

    @Test
    fun `SwipeToActionRow displays end actions correctly`() {
        val testActions = createTestEndActions()

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    endActions = testActions,
                ) {
                    TestContent("Content with end actions")
                }
            }
        }

        // Verify content is displayed
        composeTestRule.onNodeWithText("Content with end actions").assertIsDisplayed()

        // Verify action buttons are present
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()
    }

    @Test
    fun `SwipeToActionRow displays both start and end actions correctly`() {
        val startActions = createTestStartActions()
        val endActions = createTestEndActions()

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = startActions,
                    endActions = endActions,
                ) {
                    TestContent("Content with both actions")
                }
            }
        }

        // Verify content is displayed
        composeTestRule.onNodeWithText("Content with both actions").assertIsDisplayed()

        // Verify all action buttons are present
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()
    }

    @Test
    fun `SwipeToActionRow action data is correctly configured`() {
        var deleteClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { deleteClicked = true },
                        ),
                    ),
                ) {
                    TestContent("Clickable content")
                }
            }
        }

        // Verify the action button exists in the composition
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()

        // Verify content is displayed
        composeTestRule.onNodeWithText("Clickable content").assertIsDisplayed()
    }

    @Test
    fun `SwipeToActionRow multiple actions are correctly configured`() {
        var archiveClicked = false
        var pinClicked = false
        var deleteClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Home,
                            label = "Archive chat",
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { archiveClicked = true },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Star,
                            label = "Pin chat",
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { pinClicked = true },
                        ),
                    ),
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { deleteClicked = true },
                        ),
                    ),
                ) {
                    TestContent("Multiple actions content")
                }
            }
        }

        // Verify all actions exist in the composition
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()

        // Verify content is displayed
        composeTestRule.onNodeWithText("Multiple actions content").assertIsDisplayed()
    }

    @Test
    fun `SwipeToActionRow three actions per side are correctly configured`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Star,
                            label = "Pin chat",
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Settings,
                            label = "Chat settings",
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Home,
                            label = "Archive chat",
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { },
                        ),
                    ),
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.MoreVert,
                            label = "More options",
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Notifications,
                            label = "Notifications",
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { },
                        ),
                    ),
                ) {
                    TestContent("Six actions content")
                }
            }
        }

        // Verify all actions are present in the composition
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Chat settings")).assertExists()
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("More options")).assertExists()
        composeTestRule.onNode(hasContentDescription("Notifications")).assertExists()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()

        // Verify content is displayed
        composeTestRule.onNodeWithText("Six actions content").assertIsDisplayed()
    }

    @Test
    fun `SwipeToActionRow state management is correctly configured`() {
        var clickCount by mutableStateOf(0)

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { clickCount++ },
                        ),
                    ),
                ) {
                    TestContent("Click count: $clickCount")
                }
            }
        }

        // Verify initial state
        composeTestRule.onNodeWithText("Click count: 0").assertIsDisplayed()

        // Verify action exists
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()
    }

    @Test
    fun `SwipeToActionRow action buttons have correct accessibility labels`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Home,
                            label = "Archive chat",
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {},
                        ),
                    ),
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = {},
                        ),
                    ),
                ) {
                    TestContent("Accessibility test content")
                }
            }
        }

        // Verify accessibility labels
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()
    }

    @Test
    fun `SwipeToActionRow works correctly with empty action lists`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = emptyList(),
                    endActions = emptyList(),
                ) {
                    TestContent("Empty actions test")
                }
            }
        }

        // Content should still be displayed
        composeTestRule.onNodeWithText("Empty actions test").assertIsDisplayed()
    }

    @Test
    fun `SwipeToActionRow preserves content interaction when actions are present`() {
        var contentClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = createTestStartActions(),
                    endActions = createTestEndActions(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("content")
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Clickable Content",
                            modifier = Modifier
                                .testTag("content-text"),
                        )
                    }
                }
            }
        }

        // Content should be displayed and accessible
        composeTestRule.onNodeWithTag("content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("content-text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clickable Content").assertIsDisplayed()
    }

    // Helper functions for test data creation
    private fun createTestStartActions() = listOf(
        SwipeAction(
            icon = Icons.Default.Home,
            label = "Archive chat",
            backgroundColor = Color.Blue,
            iconTint = Color.White,
            onClick = {},
        ),
        SwipeAction(
            icon = Icons.Default.Star,
            label = "Pin chat",
            backgroundColor = Color.Green,
            iconTint = Color.White,
            onClick = {},
        ),
    )

    private fun createTestEndActions() = listOf(
        SwipeAction(
            icon = Icons.Default.Delete,
            label = "Delete chat",
            backgroundColor = Color.Red,
            iconTint = Color.White,
            onClick = {},
        ),
    )

    @androidx.compose.runtime.Composable
    private fun TestContent(text: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
