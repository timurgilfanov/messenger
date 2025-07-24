package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Category(Unit::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwipeActionDimensionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SwipeToActionRow uses standard width for 1-2 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                // Test with 1 start action
                SwipeToActionRow(
                    startActions = listOf(createTestAction("Action1")),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("1 action test")
                    }
                }
            }
        }

        // Verify that 72dp is used (standard width)
        composeTestRule.onNodeWithContentDescription("Action1")
            .assertWidthIsEqualTo(72.dp)
    }

    @Test
    fun `SwipeToActionRow uses standard width for 2 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction("Action1"),
                        createTestAction("Action2"),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("2 actions test")
                    }
                }
            }
        }

        // Verify that 72dp per action is used (standard width)
        composeTestRule.onNodeWithContentDescription("Action1")
            .assertWidthIsEqualTo(72.dp)
        composeTestRule.onNodeWithContentDescription("Action2")
            .assertWidthIsEqualTo(72.dp)
    }

    @Test
    fun `SwipeToActionRow uses compact width for 3 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction("Action1"),
                        createTestAction("Action2"),
                        createTestAction("Action3"),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("3 actions test")
                    }
                }
            }
        }
        // Calculate expected width: 3 actions * 60dp (compact)
        composeTestRule.onNodeWithContentDescription("Action1")
            .assertWidthIsEqualTo(60.dp)
        composeTestRule.onNodeWithContentDescription("Action2")
            .assertWidthIsEqualTo(60.dp)
        composeTestRule.onNodeWithContentDescription("Action3")
            .assertWidthIsEqualTo(60.dp)
    }

    @Test
    fun `SwipeToActionRow handles mixed action counts correctly`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction("Start1"),
                        createTestAction("Start2"),
                    ), // 2 actions -> 72dp each
                    endActions = listOf(
                        createTestAction("End1"),
                        createTestAction("End2"),
                        createTestAction("End3"),
                    ), // 3 actions -> 60dp each
                ) {
                    Box(modifier = Modifier.testTag("content-mixed-actions")) {
                        Text("Mixed actions test")
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Start1")
            .assertWidthIsEqualTo(72.dp)
        composeTestRule.onNodeWithContentDescription("Start2")
            .assertWidthIsEqualTo(72.dp)
        composeTestRule.onNodeWithContentDescription("End1")
            .assertWidthIsEqualTo(60.dp)
        composeTestRule.onNodeWithContentDescription("End1")
            .assertWidthIsEqualTo(60.dp)
        composeTestRule.onNodeWithContentDescription("End1")
            .assertWidthIsEqualTo(60.dp)
    }

    // Helper functions
    @Composable
    private fun createTestAction(label: String) = SwipeAction(
        icon = when (label.last()) {
            '1' -> Icons.Default.Home
            '2' -> Icons.Default.Star
            '3' -> Icons.Default.Settings
            else -> Icons.Default.Delete
        },
        label = label,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = {},
    )
}
