package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
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
import androidx.paging.PagingData
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
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
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
        delay(MARK_AS_VISIBLE_DEBOUNCE)
        val visibleItems = visibleItemsInfo.value
        if (visibleItems.isNotEmpty() && messages.itemCount > 0) {
            // Get the last visible message index (bottom of the screen)
            val lastVisibleIndex = visibleItems.minByOrNull { it.index }?.index
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
                state = inputTextFieldState,
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
                        message = message.toMessageUiModel(participants = state.participants),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
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
