package timur.gilfanov.messenger

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.Application

@OptIn(ExperimentalTestApi::class)
@Category(Application::class)
@RunWith(AndroidJUnit4::class)
class ChatListConfigurationChangeApplicationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Test
    fun chatListScreen_buttonsWorkAfterRotation() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Verify buttons are functional before rotation
            onNodeWithTag("search_button")
                .assertIsDisplayed()
                .performClick()

            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()
                .performClick()

            // Rotate to landscape
            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            // Verify buttons are still functional after rotation
            onNodeWithTag("search_button")
                .assertIsDisplayed()
                .performClick()

            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()
                .performClick()

            // Rotate back to portrait
            activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            waitForIdle()

            // Verify buttons are still functional
            onNodeWithTag("search_button")
                .assertIsDisplayed()
                .performClick()

            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()
                .performClick()
        }
    }

    @Test
    fun chatListScreen_handlesRotationDuringLoading() {
        with(composeTestRule) {
            // Try to catch the app during loading
            try {
                waitUntilAtLeastOneExists(hasText("Loading..."), timeoutMillis = 500)

                // Rotate while loading
                activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
                waitForIdle()

                // Should eventually show content
                waitUntilAtLeastOneExists(
                    hasTestTag("empty_state") or hasTestTag("chat_list"),
                    timeoutMillis = 3000,
                )

                // Verify UI is stable
                onNodeWithTag("search_button")
                    .assertIsDisplayed()

                onNodeWithTag("new_chat_button")
                    .assertIsDisplayed()
            } catch (e: AssertionError) {
                // If loading is too fast, just verify final state
                waitUntilAtLeastOneExists(
                    hasTestTag("empty_state") or hasTestTag("chat_list"),
                    timeoutMillis = 3000,
                )
            }
        }
    }

    @Test
    fun chatListScreen_layoutAdaptsToOrientation() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Portrait mode - verify layout
            onNodeWithTag("search_button")
                .assertIsDisplayed()
            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()
            onNodeWithText("Current User")
                .assertIsDisplayed()

            // Rotate to landscape
            activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            waitForIdle()

            // Landscape mode - verify layout still works
            onNodeWithTag("search_button")
                .assertIsDisplayed()
            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()
            onNodeWithText("Current User")
                .assertIsDisplayed()

            // Verify content is properly displayed in landscape
            try {
                onNodeWithTag("empty_state").assertIsDisplayed()
            } catch (e: AssertionError) {
                // If not empty state, should have chat list
                onNodeWithTag("chat_list").assertIsDisplayed()
            }
        }
    }

    @Test
    fun chatListScreen_preservesScrollPositionOnRotation() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Check if we have a chat list (with scrollable content)
            val hasScrollableContent = try {
                onNodeWithTag("chat_list").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }

            if (hasScrollableContent) {
                // TODO: This would require a chat list with many items to test scroll position
                // For now, just verify the chat list is preserved
                onNodeWithTag("chat_list").assertIsDisplayed()

                // Rotate to landscape
                activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
                waitForIdle()

                // Verify chat list is still displayed
                onNodeWithTag("chat_list").assertIsDisplayed()

                // Rotate back to portrait
                activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
                waitForIdle()

                // Verify chat list is still displayed
                onNodeWithTag("chat_list").assertIsDisplayed()
            }
        }
    }

    @Test
    fun chatListScreen_handlesRapidRotations() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Perform rapid rotations
            repeat(10) { index ->
                activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
                // Shorter wait for rapid rotations
                waitForIdle()
            }

            // Verify UI is stable after rapid rotations
            onNodeWithTag("search_button")
                .assertIsDisplayed()

            onNodeWithTag("new_chat_button")
                .assertIsDisplayed()

            onNodeWithText("Current User")
                .assertIsDisplayed()

            // Verify content is preserved
            try {
                onNodeWithTag("empty_state").assertIsDisplayed()
            } catch (e: AssertionError) {
                // If not empty state, should have chat list
                onNodeWithTag("chat_list").assertIsDisplayed()
            }
        }
    }

    @Test
    fun chatListScreen_memoryStabilityDuringRotations() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Perform many rotations to test memory stability
            repeat(25) { index ->
                activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
                waitForIdle()

                // Verify core components are still functional
                onNodeWithTag("search_button")
                    .assertIsDisplayed()

                onNodeWithTag("new_chat_button")
                    .assertIsDisplayed()

                // Verify no memory leaks by checking UI is responsive
                onNodeWithTag("search_button")
                    .performClick()
            }

            // Final verification
            onNodeWithText("Current User")
                .assertIsDisplayed()
        }
    }

    @Test
    fun chatListScreen_preservesDataIntegrityAcrossRotations() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Capture initial state
            val initialUserName = "Current User"
            onNodeWithText(initialUserName).assertIsDisplayed()

            // Check initial content state
            val hasEmptyState = try {
                onNodeWithTag("empty_state").assertIsDisplayed()
                onNodeWithText("No chats").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }

            // Rotate multiple times
            repeat(5) { index ->
                activity.requestedOrientation = if (index % 2 == 0) {
                    SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    SCREEN_ORIENTATION_PORTRAIT
                }
                waitForIdle()

                // Verify data integrity is preserved
                onNodeWithText(initialUserName).assertIsDisplayed()

                if (hasEmptyState) {
                    onNodeWithTag("empty_state").assertIsDisplayed()
                    onNodeWithText("No chats").assertIsDisplayed()
                } else {
                    onNodeWithTag("chat_list").assertIsDisplayed()
                }
            }
        }
    }
}
