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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.R
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

        composeTestRule.onNodeWithText("Content with start actions").assertIsDisplayed()

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

        composeTestRule.onNodeWithText("Content with end actions").assertIsDisplayed()

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
    fun `SwipeToActionRow action data is clickable`() {
        var deleteClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            labelRes = R.string.chat_list_swipe_delete_content_description,
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

        composeTestRule.onNodeWithText("Clickable content").assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Delete chat"))
            .assertExists()
            .performClick() // it's hidden behind the content and performClick() will not work
        assertEquals(false, deleteClicked)

        composeTestRule.onNodeWithText("Clickable content").performTouchInput {
            swipeLeft()
        }
        assertEquals(false, deleteClicked)
        composeTestRule.onNode(hasContentDescription("Delete chat"))
            .performClick()

        assertEquals(false, deleteClicked)
    }

    @Test
    fun `SwipeToActionRow multiple actions are clickable`() {
        var archiveClicked by mutableIntStateOf(0)
        var pinClicked by mutableIntStateOf(0)
        var deleteClicked by mutableIntStateOf(0)

        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Home,
                            labelRes = R.string.chat_list_swipe_archive_content_description,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { archiveClicked++ },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Star,
                            labelRes = R.string.chat_list_swipe_pin_content_description,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { pinClicked++ },
                        ),
                    ),
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            labelRes = R.string.chat_list_swipe_delete_content_description,
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { deleteClicked++ },
                        ),
                    ),
                ) {
                    TestContent("Multiple actions content")
                }
            }
        }

        composeTestRule.onNodeWithText("Multiple actions content").assertIsDisplayed()

        // Verify all actions exist in the composition
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists().performClick()
        assertEquals(0, archiveClicked)
        assertEquals(0, pinClicked)
        assertEquals(0, deleteClicked)

        composeTestRule.onNodeWithText("Multiple actions content").performTouchInput {
            swipeRight()
        }
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists().performClick()
        assertEquals(1, archiveClicked)
        assertEquals(1, pinClicked)
        assertEquals(0, deleteClicked)

        composeTestRule.onNodeWithText("Multiple actions content")
            .performTouchInput { swipeLeft() }
            .performTouchInput { swipeLeft() }

        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists().performClick()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists().performClick()
        assertEquals(1, archiveClicked)
        assertEquals(1, pinClicked)
        assertEquals(1, deleteClicked)
    }

    @Test
    fun `SwipeToActionRow three actions per side are correctly configured`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.Star,
                            labelRes = R.string.chat_list_swipe_pin_content_description,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Settings,
                            labelRes = R.string.chat_list_swipe_settings_content_description,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Home,
                            labelRes = R.string.chat_list_swipe_archive_content_description,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { },
                        ),
                    ),
                    endActions = listOf(
                        SwipeAction(
                            icon = Icons.Default.MoreVert,
                            labelRes = R.string.chat_list_swipe_more_content_description,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Notifications,
                            labelRes = R.string.chat_list_swipe_notifications_content_description,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { },
                        ),
                        SwipeAction(
                            icon = Icons.Default.Delete,
                            labelRes = R.string.chat_list_swipe_delete_content_description,
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

        composeTestRule.onNode(hasContentDescription("Pin chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("Chat settings")).assertExists()
        composeTestRule.onNode(hasContentDescription("Archive chat")).assertExists()
        composeTestRule.onNode(hasContentDescription("More options")).assertExists()
        composeTestRule.onNode(hasContentDescription("Notifications")).assertExists()
        composeTestRule.onNode(hasContentDescription("Delete chat")).assertExists()

        composeTestRule.onNodeWithText("Six actions content").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Empty actions test").assertIsDisplayed()
    }

    private fun createTestStartActions() = listOf(
        SwipeAction(
            icon = Icons.Default.Home,
            labelRes = R.string.chat_list_swipe_archive_content_description,
            backgroundColor = Color.Blue,
            iconTint = Color.White,
            onClick = {},
        ),
        SwipeAction(
            icon = Icons.Default.Star,
            labelRes = R.string.chat_list_swipe_pin_content_description,
            backgroundColor = Color.Green,
            iconTint = Color.White,
            onClick = {},
        ),
    )

    private fun createTestEndActions() = listOf(
        SwipeAction(
            icon = Icons.Default.Delete,
            labelRes = R.string.chat_list_swipe_delete_content_description,
            backgroundColor = Color.Red,
            iconTint = Color.White,
            onClick = {},
        ),
    )

    @Composable
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
