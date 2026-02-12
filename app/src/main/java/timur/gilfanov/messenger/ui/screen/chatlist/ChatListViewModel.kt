package timur.gilfanov.messenger.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListUseCase
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.util.Logger

private const val TAG = "ChatListViewModel"

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val flowChatListUseCase: FlowChatListUseCase,
    private val chatRepository: ChatRepository,
    private val logger: Logger,
) : ViewModel() {

    companion object {
        private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
        private val UPDATING_DEBOUNCE = 1000.milliseconds
    }

    private val _state = MutableStateFlow(ChatListScreenState(isLoading = true))
    val state = _state.asStateFlow()

    private val _effects = Channel<ChatListSideEffects>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { observeProfile() }
        viewModelScope.launch { observeChatList() }
        viewModelScope.launch { observeChatListUpdating() }
    }

    private suspend fun observeProfile() {
        observeProfileUseCase()
            .distinctUntilChanged()
            .collect { result ->
                when (result) {
                    is ResultWithError.Success -> {
                        val profile = result.data
                        val currentUser = CurrentUserUiModel(
                            id = ParticipantId(profile.id.id),
                            name = profile.name,
                            pictureUrl = profile.pictureUrl,
                        )
                        _state.update {
                            it.copy(currentUser = currentUser)
                        }
                    }

                    is ResultWithError.Failure -> {
                        when (result.error) {
                            ObserveProfileError.Unauthorized -> {
                                logger.i(
                                    TAG,
                                    "Profile observation failed with Unauthorized error",
                                )
                                _effects.send(ChatListSideEffects.Unauthorized)
                            }
                        }
                    }
                }
            }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeChatListUpdating() {
        chatRepository.isChatListUpdateApplying()
            .distinctUntilChanged()
            .debounce(UPDATING_DEBOUNCE)
            .collect { isUpdating ->
                _state.update { it.copy(isChatListUpdateApplying = isUpdating) }
            }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeChatList() {
        flowChatListUseCase()
            .distinctUntilChanged()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                result.onSuccess { chats ->
                    val chatListItems = chats.map { chat ->
                        chat.toChatListItemUiModel()
                    }.toPersistentList()

                    val uiState = if (chatListItems.isEmpty()) {
                        ChatListUiState.Empty
                    } else {
                        ChatListUiState.NotEmpty(chatListItems)
                    }
                    _state.update {
                        it.copy(
                            uiState = uiState,
                            isLoading = false,
                            error = null,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error,
                        )
                    }
                }
            }
    }
}
