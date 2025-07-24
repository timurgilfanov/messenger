package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.activity.ComponentActivity
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Category(Unit::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwipeActionDimensionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `SwipeToActionRow uses standard width for 1-2 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                // Test with 1 start action
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction(R.string.chat_list_swipe_delete_content_description),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("1 action test")
                    }
                }
            }
        }

        // Verify that 72dp is used (standard width)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_delete_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(72.dp)
    }

    @Test
    fun `SwipeToActionRow uses standard width for 2 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction(R.string.chat_list_swipe_delete_content_description),
                        createTestAction(R.string.chat_list_swipe_archive_content_description),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("2 actions test")
                    }
                }
            }
        }

        // Verify that 72dp per action is used (standard width)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_delete_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(72.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_archive_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(72.dp)
    }

    @Test
    fun `SwipeToActionRow uses compact width for 3 actions`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction(R.string.chat_list_swipe_delete_content_description),
                        createTestAction(R.string.chat_list_swipe_archive_content_description),
                        createTestAction(R.string.chat_list_swipe_pin_content_description),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("3 actions test")
                    }
                }
            }
        }
        // Calculate expected width: 3 actions * 60dp (compact)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_delete_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_archive_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_pin_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
    }

    @Test
    fun `SwipeToActionRow handles mixed action counts correctly`() {
        composeTestRule.setContent {
            MessengerTheme {
                SwipeToActionRow(
                    startActions = listOf(
                        createTestAction(R.string.chat_list_swipe_delete_content_description),
                        createTestAction(R.string.chat_list_swipe_archive_content_description),
                    ), // 2 actions -> 72dp each
                    endActions = listOf(
                        createTestAction(R.string.chat_list_swipe_pin_content_description),
                        createTestAction(R.string.chat_list_swipe_settings_content_description),
                        createTestAction(R.string.chat_list_swipe_more_content_description),
                    ), // 3 actions -> 60dp each
                ) {
                    Box(modifier = Modifier.testTag("content-mixed-actions")) {
                        Text("Mixed actions test")
                    }
                }
            }
        }

        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_delete_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(72.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_archive_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(72.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_pin_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_settings_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
        composeTestRule.onNode(
            hasContentDescription(
                composeTestRule.activity.getString(
                    R.string.chat_list_swipe_more_content_description,
                ),
            ),
        ).assertWidthIsEqualTo(60.dp)
    }

    // Helper functions
    @Composable
    private fun createTestAction(labelRes: Int) = SwipeAction(
        icon = when (labelRes) {
            R.string.chat_list_swipe_archive_content_description -> Icons.Default.Home
            R.string.chat_list_swipe_pin_content_description -> Icons.Default.Star
            R.string.chat_list_swipe_settings_content_description -> Icons.Default.Settings
            else -> Icons.Default.Delete
        },
        labelRes = labelRes,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = {},
    )
}
