package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: ChatId,
    currentUserId: ParticipantId,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel =
        hiltViewModel(
            key = "${chatId.id}_${currentUserId.id}",
            creationCallback = { factory: ChatViewModel.ChatViewModelFactory ->
                factory.create(
                    chatId = chatId.id,
                    currentUserId = currentUserId.id,
                )
            },
        ),
) {
    val uiState by viewModel.collectAsState()
    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        onMarkMessagesAsReadUpTo = viewModel::markMessagesAsReadUpTo,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is ChatUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("loading_indicator"),
                )
            }
        }

        is ChatUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("error_message"),
                )
            }
        }

        is ChatUiState.Ready -> {
            ChatContent(
                state = uiState,
                onSendMessage = onSendMessage,
                onMarkMessagesAsReadUpTo = onMarkMessagesAsReadUpTo,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // Complex UI composition requires length
fun ChatContent(
    state: ChatUiState.Ready,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Collect paged messages for LazyColumn
    val messages = state.messages.collectAsLazyPagingItems()

    // Track visible messages and mark as read
    val visibleItemsInfo = remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
        }
    }

    val currentOnMarkMessagesAsReadUpTo = rememberUpdatedState(onMarkMessagesAsReadUpTo)

    LaunchedEffect(visibleItemsInfo.value, messages.itemCount) {
        val visibleItems = visibleItemsInfo.value
        if (visibleItems.isNotEmpty() && messages.itemCount > 0) {
            // Get the last visible message index (bottom of the screen)
            val lastVisibleIndex = visibleItems.maxByOrNull { it.index }?.index
            if (lastVisibleIndex != null &&
                lastVisibleIndex >= 0 &&
                lastVisibleIndex < messages.itemCount
            ) {
                val lastVisibleMessage = messages[lastVisibleIndex]
                if (lastVisibleMessage != null) {
                    currentOnMarkMessagesAsReadUpTo.value(lastVisibleMessage.id)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.title)
                        state.updateError?.let { error ->
                            Text(
                                text = "Update failed: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInput(
                state = state.inputTextField,
                textValidationError = state.inputTextValidationError,
                isSending = state.isSending,
                onSendMessage = onSendMessage,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            items(
                count = messages.itemCount,
                key = messages.itemKey { message -> message.id.id },
            ) { index ->
                val message = messages[index]
                if (message != null) {
                    MessageBubble(
                        message = message.toMessageUiModel(
                            participants = state.participants,
                            currentUserId = getCurrentUserId(state),
                        ),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatContentPreview() {
    MessengerTheme {
        ChatContent(
            state = ChatUiState.Ready(
                id = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                title = "Alice",
                participants = persistentListOf(
                    ParticipantUiModel(
                        id = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
                        name = "Alice",
                        pictureUrl = null,
                    ),
                ),
                isGroupChat = false,
                messages = flowOf(PagingData.empty()), // Paging data will be not shown in preview
                inputTextField = TextFieldState(""),
                isSending = false,
                status = ChatStatus.OneToOne(null),
            ),
            onSendMessage = {},
            onMarkMessagesAsReadUpTo = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatLoadingPreview() {
    MessengerTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Extension function to convert a domain Message to MessageUiModel for display.
 */
private fun Message.toMessageUiModel(
    participants: ImmutableList<ParticipantUiModel>,
    currentUserId: ParticipantId,
): MessageUiModel {
    val senderParticipant = participants.find { it.id == this.sender.id }

    return MessageUiModel(
        id = this.id.id.toString(),
        text = when (this) {
            is TextMessage -> this.text
            else -> error("Unsupported message type")
        },
        senderId = this.sender.id.id.toString(),
        senderName = senderParticipant?.name ?: this.sender.name,
        createdAt = formatTimestamp(this.createdAt.toEpochMilliseconds()),
        deliveryStatus = this.deliveryStatus ?: DeliveryStatus.Sending(0),
        isFromCurrentUser = this.sender.id == currentUserId,
    )
}

/**
 * Helper function to get the current user ID from the chat state.
 * This is a temporary workaround - ideally we'd store this in the UI state.
 */
private fun getCurrentUserId(state: ChatUiState.Ready): ParticipantId {
    // For now, we'll use a heuristic to find the current user
    // This should be improved to properly track the current user ID
    return state.participants.firstOrNull()?.id ?: ParticipantId(UUID.randomUUID())
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
