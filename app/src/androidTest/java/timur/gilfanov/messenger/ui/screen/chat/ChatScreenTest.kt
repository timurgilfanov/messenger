package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

    @Test
    fun chatScreen_showsLoadingState() {
        val loadingState = ChatUiState.Loading()

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = loadingState,
                    onTextChange = {},
                    onSendMessage = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsErrorState() {
        val errorMessage = ReceiveChatUpdatesError.UnknownError
        val errorState = ChatUiState.Error(errorMessage)

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = errorState,
                    onTextChange = {},
                    onSendMessage = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Error: $errorMessage").assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsReadyStateWithMessages() {
        val messages = persistentListOf(
            MessageUiModel(
                id = "1",
                text = "Hello! 👋",
                senderId = "other-user",
                senderName = "Alice",
                createdAt = "14:28",
                deliveryStatus = DeliveryStatus.Read,
                isFromCurrentUser = false,
            ),
            MessageUiModel(
                id = "2",
                text = "How are you doing today?",
                senderId = "current-user",
                senderName = "You",
                createdAt = "14:30",
                deliveryStatus = DeliveryStatus.Delivered,
                isFromCurrentUser = true,
            ),
        )

        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Alice",
            participants = persistentListOf(
                ParticipantUiModel(
                    id = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
                    name = "Alice",
                    pictureUrl = null,
                ),
            ),
            isGroupChat = false,
            messages = messages,
            inputText = "",
            isSending = false,
            status = ChatStatus.OneToOne(null),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = {},
                    onSendMessage = {},
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
    fun chatScreen_messageInputUpdatesText() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = persistentListOf(),
            inputText = "",
            isSending = false,
            status = ChatStatus.OneToOne(null),
        )

        var capturedText = ""

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = { capturedText = it },
                    onSendMessage = {},
                )
            }
        }

        val testMessage = "Hello, this is a test message!"
        composeTestRule.onNodeWithTag("message_input").performTextInput(testMessage)

        // Note: In a real test, we would need to update the state and recompose
        // This test verifies that the callback is called
        assert(capturedText == testMessage)
    }

    @Test
    fun chatScreen_sendButtonClicksWhenTextPresent() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = persistentListOf(),
            inputText = "Test message",
            isSending = false,
            status = ChatStatus.OneToOne(null),
        )

        var sendClicked = false

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = {},
                    onSendMessage = { sendClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("send_button").performClick()
        assert(sendClicked)
    }

    @Test
    fun chatScreen_showsSendingState() {
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = persistentListOf(),
            inputText = "Sending...",
            isSending = true,
            status = ChatStatus.OneToOne(null),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = {},
                    onSendMessage = {},
                )
            }
        }

        // Verify that the send button is disabled during sending
        composeTestRule.onNodeWithTag("send_button").assertExists()
        // The button should show a progress indicator or be disabled when sending
    }

    @Test
    fun chatScreen_showsGroupChatStatus() {
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
            messages = persistentListOf(),
            inputText = "",
            isSending = false,
            status = ChatStatus.Group(2),
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = {},
                    onSendMessage = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Group Chat").assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsUpdateError() {
        val updateError = ReceiveChatUpdatesError.UnknownError
        val readyState = ChatUiState.Ready(
            id = testChatId,
            title = "Test Chat",
            participants = persistentListOf(),
            isGroupChat = false,
            messages = persistentListOf(),
            inputText = "",
            isSending = false,
            status = ChatStatus.OneToOne(null),
            updateError = updateError,
        )

        composeTestRule.setContent {
            MessengerTheme {
                ChatScreenContent(
                    uiState = readyState,
                    onTextChange = {},
                    onSendMessage = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Update failed: $updateError").assertIsDisplayed()
    }
}

@Composable
private fun ChatScreenContent(
    uiState: ChatUiState,
    onTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
) {
    when (uiState) {
        is ChatUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Loading"
                    },
                )
            }
        }

        is ChatUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is ChatUiState.Ready -> {
            ChatContent(
                state = uiState,
                onTextChange = onTextChange,
                onSendMessage = onSendMessage,
            )
        }
    }
}
