@file:Suppress("TooManyFunctions") // many of them are previews

package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
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
        onDismissDialogError = viewModel::dismissDialogError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // in Compose property drilling is preferred over wrapper
fun ChatScreenContent(
    uiState: ChatUiState,
    inputTextFieldState: TextFieldState,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
    onDismissDialogError: () -> Unit = {},
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
                onDismissDialogError = onDismissDialogError,
                modifier = modifier,
            )
        }
    }
}

private const val MARK_AS_VISIBLE_DEBOUNCE = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList") // Complex UI composition; property drilling preferred
fun ChatContent(
    state: ChatUiState.Ready,
    inputTextFieldState: TextFieldState,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
    onDismissDialogError: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    val messages = state.messages.collectAsLazyPagingItems()

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
                        state.updateError?.let {
                            Text(
                                text = stringResource(R.string.chat_update_failed),
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

    if (state.dialogError != null) {
        AlertDialog(
            onDismissRequest = onDismissDialogError,
            title = { Text(text = stringResource(R.string.send_error_dialog_title)) },
            text = { Text(text = readyErrorMessage(state.dialogError)) },
            confirmButton = {
                TextButton(onClick = onDismissDialogError) {
                    Text(text = stringResource(R.string.send_error_dialog_dismiss))
                }
            },
            modifier = Modifier.testTag("send_error_dialog"),
        )
    }
}

@Composable
private fun PagingRefreshOverlay(messages: LazyPagingItems<Message>) {
    when (messages.loadState.refresh) {
        LoadState.Loading -> {
            if (messages.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("paging_refresh_loading_indicator"),
                    )
                }
            }
        }
        is LoadState.Error -> if (messages.itemCount == 0) {
            PagingRefreshError(
                onRetry = messages::retry,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PagingLoadError(
                    onRetry = messages::retry,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    testTag = "paging_refresh_error",
                )
            }
        }
        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun PagingRefreshError(onRetry: () -> Unit) {
    PagingLoadError(
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
        when (messages.loadState.prepend) {
            LoadState.Loading -> item(key = "newer_messages_loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("paging_prepend_loading_indicator"))
                }
            }
            is LoadState.Error -> item(key = "newer_messages_error") {
                PagingLoadError(
                    onRetry = messages::retry,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    testTag = "paging_prepend_error",
                )
            }
            is LoadState.NotLoading -> Unit
        }

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

        when (messages.loadState.append) {
            LoadState.Loading -> item(key = "older_messages_loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("paging_append_loading_indicator"))
                }
            }
            is LoadState.Error -> item(key = "older_messages_error") {
                PagingLoadError(
                    onRetry = messages::retry,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    testTag = "paging_append_error",
                )
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun PagingLoadError(onRetry: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_messages_failed_to_load),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("paging_error_message"),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("paging_retry_button"),
        ) {
            Text(stringResource(R.string.chat_paging_retry))
        }
    }
}

@Composable
private fun readyErrorMessage(error: ReadyError): String = when (error) {
    is ReadyError.SendMessageError -> sendMessageErrorMessage(error.error)
}

@Composable
private fun sendMessageErrorMessage(error: SendMessageError): String = when (error) {
    is SendMessageError.WaitDebounce ->
        stringResource(R.string.send_error_dialog_wait_debounce, error.duration)
    is SendMessageError.WaitAfterJoining ->
        stringResource(R.string.send_error_dialog_wait_after_joining, error.duration)
    is SendMessageError.LocalOperationFailed -> localStorageErrorMessage(error.error)
    is SendMessageError.RemoteOperationFailed -> remoteErrorMessage(error.error)
    else -> stringResource(R.string.send_error_dialog_message)
}

@Composable
private fun localStorageErrorMessage(error: LocalStorageError): String = when (error) {
    LocalStorageError.StorageFull -> stringResource(R.string.send_error_dialog_local_storage_full)
    else -> stringResource(R.string.send_error_dialog_local_storage)
}

@Composable
private fun remoteErrorMessage(error: RemoteError): String = when (error) {
    is RemoteError.Failed.NetworkNotAvailable ->
        stringResource(R.string.send_error_dialog_network_unavailable)
    is RemoteError.Failed.ServiceDown ->
        stringResource(R.string.send_error_dialog_service_down)
    is RemoteError.Failed.Cooldown ->
        stringResource(R.string.send_error_dialog_cooldown)
    is RemoteError.Unauthenticated ->
        stringResource(R.string.send_error_dialog_session_expired)
    is RemoteError.InsufficientPermissions ->
        stringResource(R.string.send_error_dialog_no_permission)
    is RemoteError.UnknownStatus.ServiceTimeout ->
        stringResource(R.string.send_error_dialog_request_timeout)
    else -> stringResource(R.string.send_error_dialog_message)
}

@Preview(showBackground = true)
@Composable
private fun RefreshMessagesLoadingPreview() {
    val messages = flowOf(
        PagingData.empty<Message>(
            sourceLoadStates = LoadStates(
                refresh = LoadState.Loading,
                prepend = LoadState.NotLoading(false),
                append = LoadState.NotLoading(false),
            ),
        ),
    )
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Ready(
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
                messages = messages,
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
private fun RefreshMessagesErrorPreview() {
    val messages = flowOf(
        PagingData.from<Message>(
            data = emptyList(),
            sourceLoadStates = LoadStates(
                refresh = LoadState.Error(RuntimeException("No internet connection")),
                prepend = LoadState.NotLoading(false),
                append = LoadState.NotLoading(false),
            ),
        ),
    )
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Ready(
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
                messages = messages,
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
private fun ChatScreenLoadingPreview() {
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Loading(),
            inputTextFieldState = TextFieldState(""),
            onSendMessage = {},
            onMarkMessagesAsReadUpTo = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenErrorPreview() {
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Error(error = ReceiveChatUpdatesRepositoryError.ChatNotFound),
            inputTextFieldState = TextFieldState(""),
            onSendMessage = {},
            onMarkMessagesAsReadUpTo = {},
        )
    }
}

private val SAMPLE_PARTICIPANT_ID =
    ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
private val SAMPLE_CHAT_ID = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
private val SAMPLE_PARTICIPANTS = persistentListOf(
    ParticipantUiModel(
        id = SAMPLE_PARTICIPANT_ID,
        name = "Alice",
        pictureUrl = null,
    ),
)
private val SAMPLE_MESSAGE = TextMessage(
    id = MessageId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
    parentId = null,
    sender = Participant(
        id = SAMPLE_PARTICIPANT_ID,
        name = "Alice",
        pictureUrl = null,
        joinedAt = Instant.fromEpochMilliseconds(1_000_000L),
        onlineAt = null,
    ),
    recipient = SAMPLE_CHAT_ID,
    createdAt = Instant.fromEpochMilliseconds(1_000_000L),
    text = "Hello! This is a sample message.",
)

@Preview(showBackground = true)
@Composable
private fun AppendPreviousMessagesLoadingPreview() {
    val messages = flowOf(
        PagingData.from<Message>(
            data = listOf(SAMPLE_MESSAGE),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(false),
                append = LoadState.Loading,
            ),
        ),
    )
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Ready(
                id = SAMPLE_CHAT_ID,
                title = "Alice",
                participants = SAMPLE_PARTICIPANTS,
                isGroupChat = false,
                messages = messages,
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
private fun AppendPreviousMessagesErrorPreview() {
    val messages = flowOf(
        PagingData.from<Message>(
            data = listOf(SAMPLE_MESSAGE),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(false),
                append = LoadState.Error(RuntimeException("Failed to load older messages")),
            ),
        ),
    )
    MessengerTheme {
        ChatScreenContent(
            uiState = ChatUiState.Ready(
                id = SAMPLE_CHAT_ID,
                title = "Alice",
                participants = SAMPLE_PARTICIPANTS,
                isGroupChat = false,
                messages = messages,
                isSending = false,
                status = ChatStatus.OneToOne(null),
            ),
            inputTextFieldState = TextFieldState(""),
            onSendMessage = {},
            onMarkMessagesAsReadUpTo = {},
        )
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
