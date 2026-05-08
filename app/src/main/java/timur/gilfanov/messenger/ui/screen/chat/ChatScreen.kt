package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
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
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel =
        hiltViewModel(
            key = chatId.id.toString(),
            creationCallback = { factory: ChatViewModel.ChatViewModelFactory ->
                factory.create(chatId = chatId.id)
            },
        ),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val inputTextFieldState = rememberTextFieldState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { sideEffect ->
            when (sideEffect) {
                ChatSideEffect.ClearInputText -> inputTextFieldState.setTextAndPlaceCursorAtEnd("")
            }
        }
    }

    // key2 needed to launch text validation after transition to Ready state
    LaunchedEffect(key1 = inputTextFieldState.text, key2 = uiState is ChatUiState.Ready) {
        viewModel.onInputTextChanged(inputTextFieldState.text.toString())
    }

    ChatScreenContent(
        uiState = uiState,
        inputTextFieldState = inputTextFieldState,
        onSendMessage = viewModel::sendMessage,
        onMarkMessagesAsReadUpTo = viewModel::markMessagesAsReadUpTo,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    inputTextFieldState: TextFieldState,
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
                inputTextFieldState = inputTextFieldState,
                onSendMessage = onSendMessage,
                onMarkMessagesAsReadUpTo = onMarkMessagesAsReadUpTo,
                modifier = modifier,
            )
        }
    }
}

private const val MARK_AS_VISIBLE_DEBOUNCE = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // Complex UI composition requires length
fun ChatContent(
    state: ChatUiState.Ready,
    inputTextFieldState: TextFieldState,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val messages = state.messages.collectAsLazyPagingItems()
    var hasPositionedInitialMessages by rememberSaveable(state.id.id.toString()) {
        mutableStateOf(false)
    }

    LaunchedEffect(messages.loadState.refresh, messages.itemCount) {
        if (!hasPositionedInitialMessages &&
            messages.loadState.refresh is LoadState.NotLoading &&
            messages.itemCount > 0
        ) {
            listState.scrollToItem(0)
            hasPositionedInitialMessages = true
        }
    }

    val visibleItemsInfo = remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
        }
    }

    val currentOnMarkMessagesAsReadUpTo = rememberUpdatedState(onMarkMessagesAsReadUpTo)

    LaunchedEffect(visibleItemsInfo.value, messages.itemCount) {
        delay(MARK_AS_VISIBLE_DEBOUNCE)
        val visibleItems = visibleItemsInfo.value
        if (visibleItems.isNotEmpty() && messages.itemCount > 0) {
            val newestVisibleIndex = visibleItems.minOfOrNull { it.index }
            if (newestVisibleIndex != null &&
                newestVisibleIndex >= 0 &&
                newestVisibleIndex < messages.itemCount
            ) {
                val newestVisibleMessage = messages[newestVisibleIndex]
                if (newestVisibleMessage != null) {
                    currentOnMarkMessagesAsReadUpTo.value(newestVisibleMessage.id)
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
                state = inputTextFieldState,
                textValidationError = state.inputTextValidationError,
                isSending = state.isSending,
                onSendMessage = onSendMessage,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            MessageList(
                messages = messages,
                participants = state.participants,
                listState = listState,
            )
            PagingRefreshOverlay(messages = messages)
        }
    }
}

@Composable
private fun PagingRefreshOverlay(messages: LazyPagingItems<Message>) {
    if (messages.itemCount > 0) return

    when (val refresh = messages.loadState.refresh) {
        LoadState.Loading -> PagingRefreshLoading()
        is LoadState.Error -> PagingRefreshError(
            error = refresh.error,
            onRetry = messages::retry,
        )
        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun PagingRefreshLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag("paging_refresh_loading_indicator"),
        )
    }
}

@Composable
private fun PagingRefreshError(error: Throwable, onRetry: () -> Unit) {
    PagingLoadError(
        error = error,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
        testTag = "paging_refresh_error",
    )
}

@Composable
private fun MessageList(
    messages: LazyPagingItems<Message>,
    participants: ImmutableList<ParticipantUiModel>,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("message_list"),
    ) {
        items(
            count = messages.itemCount,
            key = messages.itemKey { message -> message.id.id },
        ) { index ->
            val message = messages[index]
            if (message != null) {
                MessageBubble(
                    message = message.toMessageUiModel(participants = participants),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        when (val append = messages.loadState.append) {
            LoadState.Loading -> item(key = "older_messages_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("paging_append_loading_indicator"),
                    )
                }
            }
            is LoadState.Error -> item(key = "older_messages_error") {
                PagingLoadError(
                    error = append.error,
                    onRetry = messages::retry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    testTag = "paging_append_error",
                )
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun PagingLoadError(
    error: Throwable,
    onRetry: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Messages failed to load: ${error.message ?: "Unknown error"}",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("paging_error_message"),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("paging_retry_button"),
        ) {
            Text("Retry")
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
                messages = flowOf(PagingData.empty()),
                isSending = false,
                status = ChatStatus.OneToOne(null),
            ),
            inputTextFieldState = TextFieldState(""),
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
        isFromCurrentUser = this.sender.isCurrentUser,
    )
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
