package timur.gilfanov.messenger.ui.screen.chatlist

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals as junitAssertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError.LocalOperationFailed
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Category(Component::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChatListScreenComponentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testUserId = ParticipantId(UUID.randomUUID())
    private val testChatId = ChatId(UUID.randomUUID())

    private fun createTestScreenState(
        uiState: ChatListUiState = ChatListUiState.Empty,
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        error: FlowChatListError? = null,
    ) = ChatListScreenState(
        uiState = uiState,
        currentUser = CurrentUserUiModel(
            id = testUserId,
            name = "John Doe",
            pictureUrl = null,
        ),
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        error = error,
    )

    private fun createTestChatItem(
        id: ChatId = testChatId,
        name: String = "Test Chat",
        lastMessage: String = "Test message",
        unreadCount: Int = 0,
    ) = ChatListItemUiModel(
        id = id,
        name = name,
        pictureUrl = null,
        lastMessage = lastMessage,
        lastMessageTime = Clock.System.now(),
        unreadCount = unreadCount,
        isOnline = false,
        lastOnlineTime = null,
    )

    private fun createScrollableTestData(itemCount: Int = 20) =
        persistentListOf<ChatListItemUiModel>().run {
            val builder = this.builder()
            repeat(itemCount) { index ->
                builder.add(
                    createTestChatItem(
                        id = ChatId(UUID.randomUUID()),
                        name = "Chat Item ${index + 1}",
                        lastMessage = "This is message ${index + 1} for testing scroll state",
                        unreadCount = if (index % 3 == 0) index else 0,
                    ),
                )
            }
            builder.build()
        }

    @Test
    fun `ChatListScreen displays empty state correctly`() {
        val screenState = createTestScreenState(uiState = ChatListUiState.Empty)

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("empty_state")
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays chat list correctly`() {
        val chatItem = createTestChatItem(name = "Alice Johnson")
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(persistentListOf(chatItem)),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("chat_list")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Johnson")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_item_${chatItem.id.id}")
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays user name in title`() {
        val screenState = createTestScreenState()

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("John Doe")
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays loading state correctly`() {
        val screenState = createTestScreenState(isLoading = true)

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_status_loading),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays error message correctly`() {
        val screenState =
            createTestScreenState(
                error = LocalOperationFailed(LocalStorageError.Corrupted),
            )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_error_local),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays chat count correctly`() {
        val chatItems = persistentListOf(
            createTestChatItem(id = ChatId(UUID.randomUUID()), name = "Chat 1"),
            createTestChatItem(id = ChatId(UUID.randomUUID()), name = "Chat 2"),
        )
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(chatItems),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_status_chats_count, 2),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen displays no chats status correctly`() {
        val screenState = createTestScreenState(uiState = ChatListUiState.Empty)

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_status_no_chats),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen search button triggers callback`() {
        val screenState = createTestScreenState()
        var searchClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = { searchClicked = true },
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("search_button")
            .performClick()

        assertTrue(searchClicked)
    }

    @Test
    fun `ChatListScreen new chat button triggers callback`() {
        val screenState = createTestScreenState()
        var newChatClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = { newChatClicked = true },
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_button")
            .performClick()

        assertTrue(newChatClicked)
    }

    @Test
    fun `ChatListScreen chat item click triggers callback`() {
        val chatItem = createTestChatItem()
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(persistentListOf(chatItem)),
        )
        var clickedChatId: ChatId? = null

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = { clickedChatId = it },
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("chat_item_${chatItem.id.id}")
            .performClick()

        assertEquals(testChatId, clickedChatId)
    }

    @Test
    fun `ChatListScreen displays multiple chat items correctly`() {
        val chatItems = persistentListOf(
            createTestChatItem(id = ChatId(UUID.randomUUID()), name = "Alice Johnson"),
            createTestChatItem(id = ChatId(UUID.randomUUID()), name = "Bob Smith"),
            createTestChatItem(id = ChatId(UUID.randomUUID()), name = "Carol Davis"),
        )
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(chatItems),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Alice Johnson")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob Smith")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Carol Davis")
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen handles configuration change correctly`() {
        val chatItem = createTestChatItem(name = "Test Chat")
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(persistentListOf(chatItem)),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        // Verify content is displayed
        composeTestRule.onNodeWithText("Test Chat")
            .assertIsDisplayed()

        // Simulate configuration change
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()

        // Verify content is still displayed
        composeTestRule.onNodeWithText("Test Chat")
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen preserves error state after configuration change`() {
        val screenState =
            createTestScreenState(
                error = LocalOperationFailed(LocalStorageError.Corrupted),
            )

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        // Verify error is displayed
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_error_local),
        )
            .assertIsDisplayed()

        // Simulate configuration change
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()

        // Verify error is still displayed
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.chat_list_error_local),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `ChatListScreen handles empty state action correctly`() {
        val screenState = createTestScreenState(uiState = ChatListUiState.Empty)
        var newChatClicked = 0
        var searchClicked = 0

        composeTestRule.setContent {
            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = { newChatClicked++ },
                        onSearchClick = { searchClicked++ },
                        onDeleteChat = {},
                    ),
                )
            }
        }

        // The empty state should be displayed
        composeTestRule.onNodeWithTag("empty_state")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.chat_list_search_content_description),
        ).assertIsDisplayed().performClick()
        assertEquals(1, searchClicked)

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.chat_list_new_chat_content_description),
        ).assertIsDisplayed().performClick()
        assertEquals(1, newChatClicked)
    }

    @Test
    fun `ChatListScreen with listState preserves scroll position during state restoration`() {
        val chatItems = createScrollableTestData(30)
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(chatItems),
        )
        val stateRestorationTester = StateRestorationTester(composeTestRule)

        var listState: LazyListState? = null

        stateRestorationTester.setContent {
            val state = rememberLazyListState()
            listState = state

            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    listState = state,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        // Scroll to a specific position
        composeTestRule.onNodeWithTag("chat_list")
            .performScrollToIndex(15)

        composeTestRule.waitForIdle()

        // Get the initial scroll position
        val initialFirstVisibleIndex = listState?.firstVisibleItemIndex ?: 0
        val initialScrollOffset = listState?.firstVisibleItemScrollOffset ?: 0

        // Verify we actually scrolled
        assertTrue(initialFirstVisibleIndex >= 10, "Should have scrolled past index 10")

        // Trigger state restoration
        stateRestorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.waitForIdle()

        // Verify the scroll position is preserved
        val restoredFirstVisibleIndex = listState?.firstVisibleItemIndex ?: 0
        val restoredScrollOffset = listState?.firstVisibleItemScrollOffset ?: 0

        junitAssertEquals(
            "First visible item index should be preserved",
            initialFirstVisibleIndex,
            restoredFirstVisibleIndex,
        )
        junitAssertEquals(
            "Scroll offset should be preserved",
            initialScrollOffset,
            restoredScrollOffset,
        )
    }

    @Test
    fun `ChatListScreen without listState loses scroll position during state restoration`() {
        val chatItems = createScrollableTestData(30)
        val screenState = createTestScreenState(
            uiState = ChatListUiState.NotEmpty(chatItems),
        )
        val stateRestorationTester = StateRestorationTester(composeTestRule)

        var recreatedState by mutableStateOf(false)

        stateRestorationTester.setContent {
            // Create a new state each time instead of using rememberLazyListState
            val state = if (recreatedState) {
                LazyListState()
            } else {
                rememberLazyListState()
            }

            MessengerTheme {
                ChatListScreenContent(
                    screenState = screenState,
                    listState = state,
                    actions = ChatListContentActions(
                        onChatClick = {},
                        onNewChatClick = {},
                        onSearchClick = {},
                        onDeleteChat = {},
                    ),
                )
            }
        }

        // Scroll to a specific position
        composeTestRule.onNodeWithTag("chat_list")
            .performScrollToIndex(15)

        composeTestRule.waitForIdle()

        // Simulate state recreation (like what happens without proper state management)
        @Suppress("AssignedValueIsNeverRead")
        recreatedState = true
        stateRestorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.waitForIdle()

        // Verify we're back at the top (scroll position lost)
        composeTestRule.onNodeWithText("Chat Item 1")
            .assertIsDisplayed()
    }
}
