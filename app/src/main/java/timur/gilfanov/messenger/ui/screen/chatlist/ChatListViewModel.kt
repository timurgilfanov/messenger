package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListUseCase

private const val STATE_UPDATE_DEBOUNCE = 200L
private const val UPDATING_DEBOUNCE = 1000L

@HiltViewModel(assistedFactory = ChatListViewModel.ChatListViewModelFactory::class)
class ChatListViewModel @AssistedInject constructor(
    @Assisted("currentUserId") currentUserIdUuid: UUID,
    private val flowChatListUseCase: FlowChatListUseCase,
    private val chatRepository: ChatRepository,
) : ViewModel(),
    ContainerHost<ChatListScreenState, Nothing> {

    override val container = container<ChatListScreenState, Nothing>(
        ChatListScreenState(
            currentUser = CurrentUserUiModel(
                id = ParticipantId(currentUserIdUuid),
                name = "Current User",
                pictureUrl = null,
            ),
            isLoading = true,
        ),
    ) {
        coroutineScope {
            launch { observeChatList() }
            launch { observeChatListUpdating() }
        }
    }

    @AssistedFactory
    interface ChatListViewModelFactory {
        fun create(@Assisted("currentUserId") currentUserId: UUID): ChatListViewModel
    }

    @OptIn(OrbitExperimental::class, FlowPreview::class)
    private suspend fun observeChatListUpdating() = subIntent {
        repeatOnSubscription {
            chatRepository.isChatListUpdating()
                .distinctUntilChanged()
                .debounce(UPDATING_DEBOUNCE)
                .collect { isUpdating ->
                    ensureActive()
                    withContext(Dispatchers.Main) {
                        reduce {
                            state.copy(isRefreshing = isUpdating)
                        }
                    }
                }
        }
    }

    @OptIn(OrbitExperimental::class, FlowPreview::class)
    private suspend fun observeChatList() = subIntent {
        repeatOnSubscription {
            flowChatListUseCase()
                .distinctUntilChanged()
                .debounce(STATE_UPDATE_DEBOUNCE)
                .collect { result ->
                    ensureActive()
                    withContext(Dispatchers.Main) {
                        reduce {
                            when (result) {
                                is ResultWithError.Success -> {
                                    val chats = result.data
                                    val chatListItems = chats.map { chat ->
                                        chat.toChatListItemUiModel()
                                    }.toPersistentList()

                                    val uiState = if (chatListItems.isEmpty()) {
                                        ChatListUiState.Empty
                                    } else {
                                        ChatListUiState.NotEmpty(chatListItems)
                                    }

                                    state.copy(
                                        uiState = uiState,
                                        isLoading = false,
                                        isRefreshing = false,
                                        error = null,
                                    )
                                }

                                is ResultWithError.Failure -> {
                                    state.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                        error = result.error,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}
