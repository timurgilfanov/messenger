package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase

private const val STATE_UPDATE_DEBOUNCE = 200L
private const val UPDATING_DEBOUNCE = 1000L

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val flowChatListUseCase: FlowChatListUseCase,
    private val chatRepository: ChatRepository,
) : ViewModel(),
    ContainerHost<ChatListScreenState, Nothing> {

    override val container = container<ChatListScreenState, Nothing>(
        ChatListScreenState(isLoading = true),
    ) {
        coroutineScope {
            launch { observeProfile() }
            launch { observeChatList() }
            launch { observeChatListUpdating() }
        }
    }

    @OptIn(OrbitExperimental::class)
    private suspend fun observeProfile() = subIntent {
        repeatOnSubscription {
            observeProfileUseCase()
                .distinctUntilChanged()
                .collect { result ->
                    reduce {
                        when (result) {
                            is ResultWithError.Success -> {
                                val profile = result.data
                                state.copy(
                                    currentUser = CurrentUserUiModel(
                                        id = ParticipantId(profile.id.id),
                                        name = profile.name,
                                        pictureUrl = profile.pictureUrl,
                                    ),
                                )
                            }
                            is ResultWithError.Failure -> state
                        }
                    }
                }
        }
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
                                        error = null,
                                    )
                                }

                                is ResultWithError.Failure -> {
                                    state.copy(
                                        isLoading = false,
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
