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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
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
                is ChatSideEffect.UiCommand -> when (sideEffect) {
                    ChatSideEffect.UiCommand.InputCleared ->
                        inputTextFieldState.setTextAndPlaceCursorAtEnd("")
                }
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
        onRetryMessage = viewModel::retryMessage,
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
    onRetryMessage: (MessageId) -> Unit = {},
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
                onRetryMessage = onRetryMessage,
                onDismissDialogError = onDismissDialogError,
                modifier = modifier,
            )
        }
    }
}

private const val MARK_AS_VISIBLE_DEBOUNCE = 300L

@Composable
@Suppress("LongParameterList") // in Compose property drilling is preferred over wrapper
fun ChatContent(
    state: ChatUiState.Ready,
    inputTextFieldState: TextFieldState,
    onSendMessage: () -> Unit,
    onMarkMessagesAsReadUpTo: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
    onRetryMessage: (MessageId) -> Unit = {},
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
        delay(MARK_AS_VISIBLE_DEBOUNCE.milliseconds)
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

    ChatReadyScaffold(
        title = state.title,
        showUpdateError = state.updateError != null,
        inputTextFieldState = inputTextFieldState,
        inputTextValidationError = state.inputTextValidationError,
        isSending = state.isSending,
        onSendMessage = onSendMessage,
        dialogError = state.dialogError,
        onDismissDialogError = onDismissDialogError,
        modifier = modifier,
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
                onRetryMessage = onRetryMessage,
            )
            PagingRefreshOverlay(
                refresh = messages.loadState.refresh,
                itemCount = messages.itemCount,
                onRetry = messages::retry,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // in Compose property drilling is preferred over wrapper
private fun ChatReadyScaffold(
    title: String,
    showUpdateError: Boolean,
    inputTextFieldState: TextFieldState,
    inputTextValidationError: TextValidationError?,
    isSending: Boolean,
    onSendMessage: () -> Unit,
    dialogError: ReadyError?,
    onDismissDialogError: () -> Unit,
    modifier: Modifier = Modifier,
    messagesContent: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = title)
                        if (showUpdateError) {
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
                textValidationError = inputTextValidationError,
                isSending = isSending,
                onSendMessage = onSendMessage,
            )
        },
        content = messagesContent,
    )

    if (dialogError != null) {
        AlertDialog(
            onDismissRequest = onDismissDialogError,
            title = { Text(text = stringResource(R.string.send_error_dialog_title)) },
            text = { Text(text = readyErrorMessage(dialogError)) },
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
private fun PagingRefreshOverlay(refresh: LoadState, itemCount: Int, onRetry: () -> Unit) {
    when (refresh) {
        LoadState.Loading -> {
            if (itemCount == 0) {
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
        is LoadState.Error -> if (itemCount == 0) {
            PagingRefreshError(
                onRetry = onRetry,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PagingLoadError(
                    onRetry = onRetry,
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
    onRetryMessage: (MessageId) -> Unit = {},
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
        prependLoadStateItem(messages.loadState.prepend, messages::retry)

        items(
            count = messages.itemCount,
            key = messages.itemKey { message -> message.id.id },
        ) { index ->
            val message = messages[index]
            if (message != null) {
                MessageBubble(
                    message = message.toMessageUiModel(participants = participants),
                    modifier = Modifier.padding(vertical = 4.dp),
                    onRetry = { onRetryMessage(message.id) },
                )
            }
        }

        appendLoadStateItem(messages.loadState.append, messages::retry)
    }
}

@Composable
private fun StaticMessageList(
    messages: ImmutableList<MessageUiModel>,
    prepend: LoadState,
    append: LoadState,
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
        prependLoadStateItem(prepend, onRetry = {})

        items(items = messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        appendLoadStateItem(append, onRetry = {})
    }
}

private fun LazyListScope.prependLoadStateItem(state: LoadState, onRetry: () -> Unit) {
    when (state) {
        LoadState.Loading -> item(key = "newer_messages_loading") {
            PagingLoadingRow(testTag = "paging_prepend_loading_indicator")
        }
        is LoadState.Error -> item(key = "newer_messages_error") {
            PagingLoadError(
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                testTag = "paging_prepend_error",
            )
        }
        is LoadState.NotLoading -> Unit
    }
}

private fun LazyListScope.appendLoadStateItem(state: LoadState, onRetry: () -> Unit) {
    when (state) {
        LoadState.Loading -> item(key = "older_messages_loading") {
            PagingLoadingRow(testTag = "paging_append_loading_indicator")
        }
        is LoadState.Error -> item(key = "older_messages_error") {
            PagingLoadError(
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                testTag = "paging_append_error",
            )
        }
        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun PagingLoadingRow(testTag: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag(testTag))
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

private val SAMPLE_CURRENT_USER_ID =
    ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
private val SAMPLE_BOB_ID =
    ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))

private val SAMPLE_ALICE = Participant(
    id = SAMPLE_PARTICIPANT_ID,
    name = "Alice",
    pictureUrl = null,
    joinedAt = Instant.fromEpochMilliseconds(1_000_000L),
    onlineAt = null,
)
private val SAMPLE_BOB = Participant(
    id = SAMPLE_BOB_ID,
    name = "Bob",
    pictureUrl = null,
    joinedAt = Instant.fromEpochMilliseconds(1_000_000L),
    onlineAt = null,
)
private val SAMPLE_CURRENT_USER = Participant(
    id = SAMPLE_CURRENT_USER_ID,
    name = "You",
    pictureUrl = null,
    joinedAt = Instant.fromEpochMilliseconds(1_000_000L),
    onlineAt = null,
    isCurrentUser = true,
)

private val SAMPLE_GROUP_PARTICIPANTS = persistentListOf(
    ParticipantUiModel(id = SAMPLE_PARTICIPANT_ID, name = "Alice", pictureUrl = null),
    ParticipantUiModel(id = SAMPLE_BOB_ID, name = "Bob", pictureUrl = null),
)

@Suppress("MagicNumber")
private fun sampleTextMessage(
    idSuffix: Int,
    sender: Participant,
    text: String,
    status: DeliveryStatus,
) = TextMessage(
    id = MessageId(UUID.fromString("00000000-0000-0000-0000-00000000000$idSuffix")),
    parentId = null,
    sender = sender,
    recipient = SAMPLE_CHAT_ID,
    createdAt = Instant.fromEpochMilliseconds(1_000_000L + idSuffix * 60_000L),
    deliveryStatus = status,
    text = text,
)

// Newest first: with reverseLayout the head of the list renders at the bottom.
@Suppress("MagicNumber")
private fun sampleConversation(): List<Message> = listOf(
    sampleTextMessage(4, SAMPLE_CURRENT_USER, "See you tomorrow!", DeliveryStatus.Read),
    sampleTextMessage(3, SAMPLE_ALICE, "Sounds great, thanks!", DeliveryStatus.Read),
    sampleTextMessage(2, SAMPLE_CURRENT_USER, "I'll send the docs over.", DeliveryStatus.Delivered),
    sampleTextMessage(1, SAMPLE_ALICE, "Hello! This is a sample message.", DeliveryStatus.Read),
)

@Suppress("MagicNumber")
private fun sampleGroupConversation(): List<Message> = listOf(
    sampleTextMessage(7, SAMPLE_CURRENT_USER, "On my way!", DeliveryStatus.Sent),
    sampleTextMessage(6, SAMPLE_BOB, "Let's meet at 5.", DeliveryStatus.Read),
    sampleTextMessage(5, SAMPLE_ALICE, "Hi everyone!", DeliveryStatus.Read),
)

private fun sampleConversationUiModels(): ImmutableList<MessageUiModel> =
    sampleConversation().map { it.toMessageUiModel(SAMPLE_PARTICIPANTS) }.toImmutableList()

private fun sampleGroupConversationUiModels(): ImmutableList<MessageUiModel> =
    sampleGroupConversation().map {
        it.toMessageUiModel(SAMPLE_GROUP_PARTICIPANTS)
    }.toImmutableList()

// Renders the Ready screen from static data so previews show real content. Production collects
// messages via Paging, whose async delivery never runs in a static @Preview, leaving the list
// permanently in its initial refresh-loading state. The previews drive the same stateless
// composables (ChatReadyScaffold, StaticMessageList, PagingRefreshOverlay) with explicit data.
@Composable
@Suppress("LongParameterList") // preview factory; defaults keep call sites terse
private fun ChatReadyPreview(
    messages: ImmutableList<MessageUiModel>,
    title: String = "Alice",
    showUpdateError: Boolean = false,
    dialogError: ReadyError? = null,
    refresh: LoadState = LoadState.NotLoading(false),
    prepend: LoadState = LoadState.NotLoading(false),
    append: LoadState = LoadState.NotLoading(false),
) {
    MessengerTheme {
        ChatReadyScaffold(
            title = title,
            showUpdateError = showUpdateError,
            inputTextFieldState = rememberTextFieldState(),
            inputTextValidationError = null,
            isSending = false,
            onSendMessage = {},
            dialogError = dialogError,
            onDismissDialogError = {},
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                StaticMessageList(
                    messages = messages,
                    prepend = prepend,
                    append = append,
                    listState = rememberLazyListState(),
                )
                PagingRefreshOverlay(refresh = refresh, itemCount = messages.size, onRetry = {})
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationPreview() {
    ChatReadyPreview(messages = sampleConversationUiModels())
}

@Preview(showBackground = true)
@Composable
private fun GroupConversationPreview() {
    ChatReadyPreview(messages = sampleGroupConversationUiModels(), title = "Weekend plans")
}

// The send-error AlertDialog renders in a separate Dialog window that a static @Preview does not
// composite; view it via Interactive Preview, which runs the real production dialog path.
@Preview(showBackground = true)
@Composable
private fun SendErrorDialogPreview() {
    ChatReadyPreview(
        messages = sampleConversationUiModels(),
        dialogError = ReadyError.SendMessageError(
            SendMessageError.RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UpdateErrorPreview() {
    ChatReadyPreview(messages = sampleConversationUiModels(), showUpdateError = true)
}

@Preview(showBackground = true)
@Composable
private fun PrependNewerMessagesLoadingPreview() {
    ChatReadyPreview(messages = sampleConversationUiModels(), prepend = LoadState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun PrependNewerMessagesErrorPreview() {
    ChatReadyPreview(
        messages = sampleConversationUiModels(),
        prepend = LoadState.Error(RuntimeException("Failed to load newer messages")),
    )
}

@Preview(showBackground = true)
@Composable
private fun AppendOlderMessagesLoadingPreview() {
    ChatReadyPreview(messages = sampleConversationUiModels(), append = LoadState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun AppendOlderMessagesErrorPreview() {
    ChatReadyPreview(
        messages = sampleConversationUiModels(),
        append = LoadState.Error(RuntimeException("Failed to load older messages")),
    )
}

@Preview(showBackground = true)
@Composable
private fun RefreshLoadingPreview() {
    ChatReadyPreview(messages = persistentListOf(), refresh = LoadState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun RefreshErrorEmptyPreview() {
    ChatReadyPreview(
        messages = persistentListOf(),
        refresh = LoadState.Error(RuntimeException("No internet connection")),
    )
}

@Preview(showBackground = true)
@Composable
private fun RefreshErrorWithMessagesPreview() {
    ChatReadyPreview(
        messages = sampleConversationUiModels(),
        refresh = LoadState.Error(RuntimeException("No internet connection")),
    )
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
