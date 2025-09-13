package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Test
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

class SwipeToActionRowScreenshotTest : ScreenshotTestBase() {

    @Composable
    private fun TestContent(text: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .height(72.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    @Test
    fun singleAction_end_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SwipeToActionRow(
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Single delete action")
            }
        }
    }

    @Test
    fun singleAction_end_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SwipeToActionRow(
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Single delete action")
            }
        }
    }

    @Test
    fun singleAction_start_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Single archive action")
            }
        }
    }

    @Test
    fun singleAction_start_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Single archive action")
            }
        }
    }

    @Test
    fun twoActions_bothSides_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Archive & Pin | Delete")
            }
        }
    }

    @Test
    fun twoActions_bothSides_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Archive & Pin | Delete")
            }
        }
    }

    @Test
    fun threeActions_compact_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Settings,
                        labelRes = R.string.chat_list_swipe_mute_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.MoreVert,
                        labelRes = R.string.chat_list_swipe_more_content_description,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Notifications,
                        labelRes = R.string.chat_list_swipe_notifications_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("3 actions each side (compact)")
            }
        }
    }

    @Test
    fun threeActions_compact_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Settings,
                        labelRes = R.string.chat_list_swipe_mute_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.MoreVert,
                        labelRes = R.string.chat_list_swipe_more_content_description,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Notifications,
                        labelRes = R.string.chat_list_swipe_notifications_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("3 actions each side (compact)")
            }
        }
    }

    @Test
    fun noActions_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            SwipeToActionRow {
                TestContent("No swipe actions")
            }
        }
    }

    @Test
    fun noActions_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            SwipeToActionRow {
                TestContent("No swipe actions")
            }
        }
    }

    @Test
    fun smallScreen_multipleActions_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.LIGHT,
            ),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Settings,
                        labelRes = R.string.chat_list_swipe_settings_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Small screen actions")
            }
        }
    }

    @Test
    fun largeScreen_multipleActions_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.DARK,
            ),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Home,
                        labelRes = R.string.chat_list_swipe_archive_content_description,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Notifications,
                        labelRes = R.string.chat_list_swipe_notifications_content_description,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {},
                    ),
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("Large screen with multiple actions")
            }
        }
    }

    @Test
    fun rtl_multipleActions_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("RTL layout actions")
            }
        }
    }

    @Test
    fun rtl_multipleActions_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            SwipeToActionRow(
                startActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Star,
                        labelRes = R.string.chat_list_swipe_pin_content_description,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {},
                    ),
                ),
                endActions = listOf(
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.chat_list_swipe_delete_content_description,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {},
                    ),
                ),
            ) {
                TestContent("RTL layout actions")
            }
        }
    }
}
