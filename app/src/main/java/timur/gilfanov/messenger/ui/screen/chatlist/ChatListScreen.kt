package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError.RemoteUnreachable
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListUiState.Empty
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListUiState.NotEmpty
import timur.gilfanov.messenger.ui.theme.MessengerTheme

data class ChatListActions(
    val onChatClick: (ChatId) -> Unit = {},
    val onNewChatClick: () -> Unit = {},
    val onSearchClick: () -> Unit = {},
)

data class ChatListContentActions(
    val onChatClick: (ChatId) -> Unit,
    val onNewChatClick: () -> Unit,
    val onSearchClick: () -> Unit,
    val onDeleteChat: (ChatId) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    currentUserId: ParticipantId,
    actions: ChatListActions,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel =
        hiltViewModel(creationCallback = { factory: ChatListViewModel.ChatListViewModelFactory ->
            factory.create(currentUserId = currentUserId.id)
        }),
) {
    val screenState by viewModel.collectAsState()

    ChatListScreenContent(
        screenState = screenState,
        actions = ChatListContentActions(
            onChatClick = actions.onChatClick,
            onNewChatClick = actions.onNewChatClick,
            onSearchClick = actions.onSearchClick,
            onDeleteChat = { /* Delete chat not implemented yet */ },
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreenContent(
    screenState: ChatListScreenState,
    actions: ChatListContentActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopBar(screenState, actions.onSearchClick, actions.onNewChatClick) },
    ) { paddingValues ->
        when (screenState.uiState) {
            Empty -> {
                EmptyStateComponent(
                    onStartFirstChat = actions.onNewChatClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag("empty_state"),
                )
            }

            is NotEmpty -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag("chat_list"),
                ) {
                    items(
                        items = screenState.uiState.chats,
                        key = { it.id.id },
                    ) { chatItem ->
                        ChatListItem(
                            chatItem = chatItem,
                            onClick = actions.onChatClick,
                            onDelete = actions.onDeleteChat,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("chat_item_${chatItem.id.id}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopBar(
    screenState: ChatListScreenState,
    onSearchClick: () -> Unit,
    onNewChatClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                // TODO center horizontally
                Text(
                    text = screenState.currentUser.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )

                val statusText = when {
                    screenState.isLoading -> stringResource(R.string.chat_list_status_loading)
                    screenState.error != null -> getErrorMessage(screenState.error)
                    else -> getStatusText(screenState.uiState)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (screenState.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("search_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search chats",
                )
            }

            IconButton(
                onClick = onNewChatClick,
                modifier = Modifier.testTag("new_chat_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New chat",
                )
            }
        },
    )
}

@Composable
private fun getStatusText(uiState: ChatListUiState): String = when (uiState) {
    Empty -> stringResource(R.string.chat_list_status_no_chats)
    is NotEmpty -> stringResource(R.string.chat_list_status_chats_count, uiState.chats.size)
}

@Composable
private fun getErrorMessage(error: FlowChatListError): String = when (error) {
    NetworkNotAvailable -> stringResource(R.string.chat_list_error_network)
    RemoteError -> stringResource(R.string.chat_list_error_server)
    RemoteUnreachable -> stringResource(R.string.chat_list_error_server_unreachable)
    LocalError -> stringResource(R.string.chat_list_error_local)
}

@Preview(showBackground = true)
@Composable
private fun ChatListScreenEmptyPreview() {
    MessengerTheme {
        ChatListScreenContent(
            screenState = ChatListScreenState(
                uiState = Empty,
                currentUser = CurrentUserUiModel(
                    id = ParticipantId(UUID.randomUUID()),
                    name = "John Doe",
                    pictureUrl = null,
                ),
                isLoading = false,
                isRefreshing = false,
                error = null,
            ),
            actions = ChatListContentActions(
                onChatClick = {},
                onNewChatClick = {},
                onSearchClick = {},
                onDeleteChat = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListScreenWithChatsPreview() {
    MessengerTheme {
        ChatListScreenContent(
            screenState = ChatListScreenState(
                uiState = NotEmpty(
                    chats = persistentListOf(
                        ChatListItemUiModel(
                            id = ChatId(UUID.randomUUID()),
                            name = "Alice Johnson",
                            pictureUrl = null,
                            lastMessage = "Hey! How are you doing?",
                            lastMessageTime = Clock.System.now(),
                            unreadCount = 2,
                            isOnline = true,
                            lastOnlineTime = Clock.System.now(),
                        ),
                        ChatListItemUiModel(
                            id = ChatId(UUID.randomUUID()),
                            name = "Project Team",
                            pictureUrl = null,
                            lastMessage = "Bob: The meeting is scheduled for 3 PM",
                            lastMessageTime = Clock.System.now(),
                            unreadCount = 0,
                            isOnline = false,
                            lastOnlineTime = null,
                        ),
                        ChatListItemUiModel(
                            id = ChatId(UUID.randomUUID()),
                            name = "Sarah Wilson",
                            pictureUrl = null,
                            lastMessage = "Thanks for the help!",
                            lastMessageTime = Clock.System.now(),
                            unreadCount = 0,
                            isOnline = false,
                            lastOnlineTime = null,
                        ),
                    ),
                ),
                currentUser = CurrentUserUiModel(
                    id = ParticipantId(UUID.randomUUID()),
                    name = "John Doe",
                    pictureUrl = null,
                ),
                isLoading = false,
                isRefreshing = false,
                error = null,
            ),
            actions = ChatListContentActions(
                onChatClick = {},
                onNewChatClick = {},
                onSearchClick = {},
                onDeleteChat = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListScreenLoadingPreview() {
    MessengerTheme {
        ChatListScreenContent(
            screenState = ChatListScreenState(
                uiState = Empty,
                currentUser = CurrentUserUiModel(
                    id = ParticipantId(UUID.randomUUID()),
                    name = "John Doe",
                    pictureUrl = null,
                ),
                isLoading = true,
                isRefreshing = false,
                error = null,
            ),
            actions = ChatListContentActions(
                onChatClick = {},
                onNewChatClick = {},
                onSearchClick = {},
                onDeleteChat = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListScreenErrorPreview() {
    MessengerTheme {
        ChatListScreenContent(
            screenState = ChatListScreenState(
                uiState = Empty,
                currentUser = CurrentUserUiModel(
                    id = ParticipantId(UUID.randomUUID()),
                    name = "John Doe",
                    pictureUrl = null,
                ),
                isLoading = false,
                isRefreshing = false,
                error = NetworkNotAvailable,
            ),
            actions = ChatListContentActions(
                onChatClick = {},
                onNewChatClick = {},
                onSearchClick = {},
                onDeleteChat = {},
            ),
        )
    }
}
