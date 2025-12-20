package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.UUID
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.BuildConfig
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListUiState.Empty
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListUiState.NotEmpty
import timur.gilfanov.messenger.ui.theme.MessengerTheme

/**
 * Actions for chat list screen.
 *
 * Marked as [Stable] rather than `@Immutable` because:
 * - Lambda functions may capture mutable state (e.g., NavBackStack)
 * - The lambda references themselves remain stable across recompositions
 * - `@Immutable` would be too strong a promise when lambdas capture mutable state
 * - `@Stable` allows Compose to skip recompositions while being semantically accurate
 */
@Stable
data class ChatListActions(
    val onChatClick: (ChatId) -> Unit = {},
    val onNewChatClick: () -> Unit = {},
    val onSearchClick: () -> Unit = {},
)

/**
 * Internal actions for chat list content.
 *
 * Marked as [Stable] rather than `@Immutable` because:
 * - Lambda functions are derived from [ChatListActions] which capture mutable state
 * - Allows Compose to optimize recompositions without false immutability promises
 */
@Stable
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
        hiltViewModel(
            key = currentUserId.id.toString(),
            creationCallback = { factory: ChatListViewModel.ChatListViewModelFactory ->
                factory.create(currentUserId = currentUserId.id)
            },
        ),
) {
    val screenState by viewModel.collectAsState()

    val contentActions = remember(actions) {
        ChatListContentActions(
            onChatClick = actions.onChatClick,
            onNewChatClick = actions.onNewChatClick,
            onSearchClick = actions.onSearchClick,
            onDeleteChat = { /* Delete chat not implemented yet */ },
        )
    }
    ChatListScreenContent(
        screenState = screenState,
        actions = contentActions,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreenContent(
    screenState: ChatListScreenState,
    actions: ChatListContentActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FEATURE_SETTINGS) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TopBar(
                screenState = screenState,
                onSearchClick = actions.onSearchClick,
                onNewChatClick = actions.onNewChatClick,
            )
            ChatListContent(
                screenState = screenState,
                actions = actions,
                listState = listState,
            )
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    screenState = screenState,
                    onSearchClick = actions.onSearchClick,
                    onNewChatClick = actions.onNewChatClick,
                )
            },
        ) { paddingValues ->
            ChatListContent(
                screenState = screenState,
                actions = actions,
                listState = listState,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun ChatListContent(
    screenState: ChatListScreenState,
    actions: ChatListContentActions,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    when (screenState.uiState) {
        Empty -> EmptyStateComponent(
            onStartFirstChat = actions.onNewChatClick,
            modifier = modifier.testTag("empty_state"),
        )

        is NotEmpty -> LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
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
                    contentDescription = stringResource(
                        R.string.chat_list_search_content_description,
                    ),
                )
            }

            IconButton(
                onClick = onNewChatClick,
                modifier = Modifier.testTag("new_chat_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(
                        R.string.chat_list_new_chat_content_description,
                    ),
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
    is FlowChatListError.LocalOperationFailed -> stringResource(R.string.chat_list_error_local)
    is FlowChatListError.RemoteOperationFailed -> when (error.error) {
        is RemoteError.Failed.Cooldown -> TODO()
        RemoteError.Failed.NetworkNotAvailable -> stringResource(R.string.chat_list_error_network)
        RemoteError.Failed.ServiceDown -> stringResource(
            R.string.chat_list_error_server_unreachable,
        )
        is RemoteError.Failed.UnknownServiceError -> stringResource(R.string.chat_list_error_server)
        RemoteError.InsufficientPermissions -> TODO()
        RemoteError.Unauthenticated -> TODO()
        RemoteError.UnknownStatus.ServiceTimeout -> TODO()
    }
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

@Preview(showBackground = true, widthDp = 1000, heightDp = 400)
@Composable
private fun ChatListScreenEmptyPortraitPreview() {
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
                error = FlowChatListError.RemoteOperationFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
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
