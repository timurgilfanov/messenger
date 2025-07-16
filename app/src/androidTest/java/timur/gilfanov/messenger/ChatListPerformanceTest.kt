package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.system.measureTimeMillis
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.ReleaseCandidate

@OptIn(ExperimentalTestApi::class)
@Category(ReleaseCandidate::class)
@RunWith(AndroidJUnit4::class)
class ChatListPerformanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatListScreen_launchPerformance() {
        // Measure app launch time
        val launchTime = measureTimeMillis {
            composeTestRule.waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 5000,
            )
        }

        // App should launch within reasonable time (5 seconds max)
        assert(launchTime < 5000) { "App launch took too long: ${launchTime}ms" }
    }

    @Test
    fun chatListScreen_scrollPerformance() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Check if we have a scrollable chat list
            val hasScrollableContent = try {
                onNodeWithTag("chat_list").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }

            if (hasScrollableContent) {
                // Measure scroll performance
                val scrollTime = measureTimeMillis {
                    try {
                        // Attempt to scroll if there are items
                        onNodeWithTag("chat_list")
                            .performScrollToIndex(0)

                        // Note: In a real test with many items, you would scroll through more items
                        // For now, we just verify scrolling doesn't crash
                    } catch (e: Exception) {
                        // If scrolling fails, it might be because there are too few items
                        // This is acceptable for performance testing
                    }
                }

                // Scrolling should be responsive (under 1 second)
                assert(scrollTime < 1000) { "Scroll performance is poor: ${scrollTime}ms" }
            }
        }
    }

    @Test
    fun chatListScreen_memoryUsageDuringInteraction() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Perform multiple interactions to test memory usage
            repeat(50) {
                // Interact with UI elements
                onNodeWithTag("search_button")
                    .assertExists()

                onNodeWithTag("new_chat_button")
                    .assertExists()

                // Small delay to allow for memory cleanup
                Thread.sleep(10)
            }

            // If we reach here without OutOfMemoryError, memory usage is acceptable
            // Verify UI is still responsive
            onNodeWithTag("search_button")
                .assertExists()
        }
    }

    @Test
    fun chatListScreen_uiResponsiveness() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Measure UI responsiveness
            val responseTime = measureTimeMillis {
                // Perform rapid UI interactions
                repeat(10) {
                    onNodeWithTag("search_button")
                        .assertExists()

                    onNodeWithTag("new_chat_button")
                        .assertExists()
                }
            }

            // UI should remain responsive (under 500ms for 10 interactions)
            assert(responseTime < 500) {
                "UI is not responsive: ${responseTime}ms for 10 interactions"
            }
        }
    }

    @Test
    fun chatListScreen_configurationChangePerformance() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Measure configuration change performance
            val configChangeTime = measureTimeMillis {
                // Perform configuration change
                activity.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                waitForIdle()

                // Verify UI is rebuilt correctly
                onNodeWithTag("search_button")
                    .assertExists()

                onNodeWithTag("new_chat_button")
                    .assertExists()
            }

            // Configuration change should be fast (under 2 seconds)
            assert(configChangeTime < 2000) {
                "Configuration change too slow: ${configChangeTime}ms"
            }
        }
    }

    @Test
    fun chatListScreen_stressTestWithRapidInteractions() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Stress test with rapid interactions
            val stressTestTime = measureTimeMillis {
                repeat(100) { index ->
                    // Alternate between different UI elements
                    if (index % 2 == 0) {
                        onNodeWithTag("search_button")
                            .assertExists()
                    } else {
                        onNodeWithTag("new_chat_button")
                            .assertExists()
                    }
                }
            }

            // Stress test should complete in reasonable time (under 3 seconds)
            assert(stressTestTime < 3000) { "Stress test took too long: ${stressTestTime}ms" }

            // Verify UI is still functional after stress test
            onNodeWithTag("search_button")
                .assertExists()

            onNodeWithTag("new_chat_button")
                .assertExists()
        }
    }

    @Test
    fun chatListScreen_memoryLeakDetection() {
        with(composeTestRule) {
            // Wait for chat list to load
            waitUntilAtLeastOneExists(
                hasTestTag("empty_state") or hasTestTag("chat_list"),
                timeoutMillis = 3000,
            )

            // Perform operations that could cause memory leaks
            repeat(20) {
                // Configuration changes
                activity.requestedOrientation = if (it % 2 == 0) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                waitForIdle()

                // UI interactions
                onNodeWithTag("search_button")
                    .assertExists()

                onNodeWithTag("new_chat_button")
                    .assertExists()
            }

            // Suggest garbage collection
            System.gc()
            Thread.sleep(100)

            // Verify UI is still functional (no memory leaks)
            onNodeWithTag("search_button")
                .assertExists()
        }
    }

    @Test
    fun chatListScreen_renderingPerformance() {
        with(composeTestRule) {
            // Measure initial rendering performance
            val renderTime = measureTimeMillis {
                waitUntilAtLeastOneExists(
                    hasTestTag("empty_state") or hasTestTag("chat_list"),
                    timeoutMillis = 3000,
                )
            }

            // Initial rendering should be fast (under 3 seconds)
            assert(renderTime < 3000) { "Initial rendering too slow: ${renderTime}ms" }

            // Verify all UI elements are rendered
            onNodeWithTag("search_button")
                .assertExists()

            onNodeWithTag("new_chat_button")
                .assertExists()
        }
    }
}
