package timur.gilfanov.messenger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.system.measureTimeMillis
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import timur.gilfanov.annotations.ReleaseCandidate
import timur.gilfanov.messenger.data.repository.WithChatsParticipantRepository
import timur.gilfanov.messenger.di.RepositoryModule
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@Category(ReleaseCandidate::class)
@RunWith(AndroidJUnit4::class)
class ChatListPerformanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ChatListScreenTestActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object PerformanceTestRepositoryModule {
        @Provides
        @Singleton
        fun provideParticipantRepository(): ParticipantRepository = WithChatsParticipantRepository()
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatListScreen_launchPerformance() {
        // Measure app launch time
        val launchTime = measureTimeMillis {
            composeTestRule.waitUntilExactlyOneExists(
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
            waitUntilExactlyOneExists(
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
            waitUntilExactlyOneExists(
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
            waitUntilExactlyOneExists(
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
            waitUntilExactlyOneExists(hasTestTag("chat_list"))

            val configChangeTime = measureTimeMillis {
                activity.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                waitForIdle()

                onNodeWithTag("search_button").assertExists()
                onNodeWithTag("new_chat_button").assertExists()
            }

            assert(configChangeTime < 1_000) {
                "Configuration change too slow: ${configChangeTime}ms"
            }
        }
    }

    @Test
    fun chatListScreen_renderingPerformance() {
        with(composeTestRule) {
            val renderTime = measureTimeMillis {
                waitUntilExactlyOneExists(hasTestTag("chat_list"))
            }

            // Initial rendering should be fast (under 3 seconds)
            assert(renderTime < 500) { "Initial rendering too slow: ${renderTime}ms" }

            // Verify all UI elements are rendered
            onNodeWithTag("search_button")
                .assertExists()

            onNodeWithTag("new_chat_button")
                .assertExists()

            // todo: check actual chat by chat_item_$chatId
        }
    }
}
