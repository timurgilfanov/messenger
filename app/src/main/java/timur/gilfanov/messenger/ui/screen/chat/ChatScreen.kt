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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: ChatId,
    currentUserId: ParticipantId,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel =
        hiltViewModel(creationCallback = { factory: ChatViewModel.ChatViewModelFactory ->
            factory.create(
                chatId = chatId.id,
                currentUserId = currentUserId.id,
            )
        }),
) {
    val uiState by viewModel.collectAsState()
    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: () -> Unit,
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
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    state: ChatUiState.Ready,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
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
            val textValidationError = when (state.inputTextValidationError) {
                null, TextValidationError.Empty -> null
                is TextValidationError.TooLong -> stringResource(
                    R.string.message_input_error_too_long,
                    state.inputTextValidationError.maxLength,
                )
            }
            MessageInput(
                state = state.inputTextField,
                textValidationError = textValidationError,
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
            items(items = state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
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
                messages = persistentListOf(
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
                    MessageUiModel(
                        id = "3",
                        text = "I'm doing great, thanks for asking!",
                        senderId = "other-user",
                        senderName = "Alice",
                        createdAt = "14:32",
                        deliveryStatus = DeliveryStatus.Read,
                        isFromCurrentUser = false,
                    ),
                ),
                inputTextField = TextFieldState(""),
                isSending = false,
                status = ChatStatus.OneToOne(null),
            ),
            onSendMessage = {},
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
