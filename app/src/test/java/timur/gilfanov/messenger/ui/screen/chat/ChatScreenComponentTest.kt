package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.paging.PagingData
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(Component::class)
class ChatScreenComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

    @Test
    fun `chat screen shows loading state`() {
        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = ChatUiState.Loading(),
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `chat screen shows error state`() {
        val errorMessage = RemoteOperationFailed(RemoteError.InsufficientPermissions)
        val errorState = ChatUiState.Error(errorMessage)

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = errorState,
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Error: $errorMessage").assertIsDisplayed()
    }

    @Test
    fun `chat screen shows ready state with messages`() {
        // Create participants and messages using fixtures
        val (alice, currentUser) = ChatScreenTestFixtures.createAliceAndCurrentUser()
        val messages = ChatScreenTestFixtures.createSampleMessages(alice, currentUser)

        val readyState = ChatScreenTestFixtures.createChatUiStateReady(
            id = testChatId,
            title = "Alice",
            messages = messages,
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        // Verify chat title is displayed in top bar (use first occurrence)
        composeTestRule.onAllNodesWithText("Alice")[0].assertIsDisplayed()

        // Verify messages are displayed
        composeTestRule.onNodeWithText("Hello! 👋").assertIsDisplayed()
        composeTestRule.onNodeWithText("How are you doing today?").assertIsDisplayed()

        // Verify message input is displayed
        composeTestRule.onNodeWithTag("message_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
    }

    @Test
    fun `chat screen message input updates text`() {
        val textFieldState = TextFieldState("")
        val readyState = ChatScreenTestFixtures.createChatUiStateReady(
            id = testChatId,
            title = "Test Chat",
            participants = emptyList(),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = textFieldState,
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        val testMessage = "Hello, this is a test message!"
        composeTestRule.onNodeWithTag("message_input").performTextInput(testMessage)

        // Note: In a real test, we would need to update the state and recompose
        // This test verifies that the callback is called
        assert(textFieldState.text == testMessage)
    }

    @Test
    fun `repeat message input text updates`() {
        val textFieldState = TextFieldState("")
        val readyState = ChatScreenTestFixtures.createChatUiStateReady(
            id = testChatId,
            title = "Test Chat",
            participants = emptyList(),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = textFieldState,
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        repeat(100) {
            val message = "Test message #$it"
            composeTestRule.onNodeWithTag("message_input").performTextInput(message)
            composeTestRule.onNodeWithTag("message_input").assertTextEquals(message)
            composeTestRule.onNodeWithTag("message_input").performTextClearance()
        }
    }

    @Test
    fun `chat screen send button clicks when text present`() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = flowOf(PagingData.empty()),
            isSending = false,
            status = ChatStatus.OneToOne(null),
        )

        var sendClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = TextFieldState("Test message"),
                    onSendMessage = { sendClicked = true },
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("send_button").performClick()
        assert(sendClicked)
    }

    @Test
    fun `chat screen shows sending state`() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = flowOf(PagingData.empty()),
            isSending = true,
            status = ChatStatus.OneToOne(null),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = TextFieldState("Sending..."),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sending_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send message")
            .assertDoesNotExist()
    }

    @Test
    fun `chat screen shows group chat status`() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Group Chat",
            participants = persistentListOf(
                ParticipantUiModel(
                    id = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
                    name = "Alice",
                    pictureUrl = null,
                ),
                ParticipantUiModel(
                    id = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003")),
                    name = "Bob",
                    pictureUrl = null,
                ),
            ),
            isGroupChat = true,
            messages = flowOf(PagingData.empty()),
            isSending = false,
            status = ChatStatus.Group(2),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Group Chat").assertIsDisplayed()
    }

    @Test
    fun `chat screen shows update error`() {
        val updateError = RemoteOperationFailed(RemoteError.InsufficientPermissions)
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = flowOf(PagingData.empty()),
            isSending = false,
            status = ChatStatus.OneToOne(null),
            updateError = updateError,
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Update failed: $updateError").assertIsDisplayed()
    }

    @Test
    fun `chat screen with empty text input have disabled send button and don't show error`() {
        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = ChatUiState.Ready(
                        id = testChatId,
                        title = "Test Chat",
                        participants = persistentListOf(),
                        isGroupChat = false,
                        messages = flowOf(PagingData.empty()),
                        inputTextValidationError = TextValidationError.Empty,
                        status = ChatStatus.OneToOne(null),
                    ),
                    inputTextFieldState = TextFieldState(""),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun `chat screen with valid text input have enabled send button and don't show error`() {
        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = ChatUiState.Ready(
                        id = testChatId,
                        title = "Test Chat",
                        participants = persistentListOf(),
                        isGroupChat = false,
                        messages = flowOf(PagingData.empty()),
                        status = ChatStatus.OneToOne(null),
                    ),
                    inputTextFieldState = TextFieldState("a"),
                    onSendMessage = {},
                    onMarkMessagesAsReadUpTo = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("send_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }
}
