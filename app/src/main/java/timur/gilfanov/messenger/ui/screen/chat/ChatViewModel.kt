package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.ui.screen.chat.ChatUiState.Error
import timur.gilfanov.messenger.ui.screen.chat.ChatUiState.Loading
import timur.gilfanov.messenger.util.repeatOnSubscription

private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
private const val KEY_CHAT_ID = "chatId"
private const val KEY_CURRENT_USER_ID = "currentUserId"

/*
    Message is sending from sendMessageUseCase call to receiving any value from the use case.

    Business requirements:
    - when message is sending input must be disabled (responsibility of View, not ViewModel)
    - when sent message appears in chat input must be cleared
    - sending new message must be queued while any other message is sending
    - sending messages must be not conflated by object equality
    - input text changes must be processed sequentially
    - only last error dialog dismissal matters, no need to queue them
 */
@Suppress("LongParameterList") // a lot of use cases is valid for ViewModel
@HiltViewModel(assistedFactory = ChatViewModel.ChatViewModelFactory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted("chatId") chatIdUuid: UUID,
    @Assisted("currentUserId") currentUserIdUuid: UUID,
    private val savedStateHandle: SavedStateHandle,
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveChatUpdatesUseCase: ReceiveChatUpdatesUseCase,
    private val getPagedMessagesUseCase: GetPagedMessagesUseCase,
    private val markMessagesAsReadUseCase: MarkMessagesAsReadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Loading())
    val state = _state.asStateFlow()

    private val _effects = Channel<ChatSideEffect>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                observeChatUpdates()
            }
        }
    }

    private var currentInputText: String = ""

    private val chatId: ChatId = savedStateHandle.get<String>(KEY_CHAT_ID)?.let {
        ChatId(UUID.fromString(it))
    } ?: ChatId(chatIdUuid).also {
        savedStateHandle[KEY_CHAT_ID] = it.id.toString()
    }

    private val currentUserId: ParticipantId =
        savedStateHandle.get<String>(KEY_CURRENT_USER_ID)?.let {
            ParticipantId(UUID.fromString(it))
        } ?: ParticipantId(currentUserIdUuid).also {
            savedStateHandle[KEY_CURRENT_USER_ID] = it.id.toString()
        }

    @AssistedFactory
    interface ChatViewModelFactory {
        fun create(
            @Assisted("chatId") chatId: UUID,
            @Assisted("currentUserId") currentUserId: UUID,
        ): ChatViewModel
    }

    private fun validateInputText(text: String): TextValidationError? {
        val message = textMessage(
            messageId = MessageId(UUID.randomUUID()),
            now = Clock.System.now(),
            text = text,
        )
        return when (val validate = message.validate()) {
            is ResultWithError.Failure<Unit, ValidationError> -> {
                validate.error as TextValidationError
            }

            is ResultWithError.Success<Unit, ValidationError> -> null
        }
    }

    fun onInputTextChanged(text: String) {
        currentInputText = text
        _state.update { state ->
            if (state is ChatUiState.Ready) {
                state.copy(inputTextValidationError = validateInputText(text))
            } else {
                state
            }
        }
    }

    private var currentChat: Chat? = null

    fun sendMessage(
        messageId: MessageId = MessageId(UUID.randomUUID()),
        now: Instant = Clock.System.now(),
    ) {
        if (_state.value !is ChatUiState.Ready) return
        viewModelScope.launch {
            _state.update { (it as? ChatUiState.Ready)?.copy(isSending = true) ?: it }
            val message = textMessage(messageId, now, currentInputText)
            sendMessageUseCase(currentChat!!, message).collect { result ->
                when (result) {
                    is ResultWithError.Success -> {
                        _state.update { (it as? ChatUiState.Ready)?.copy(isSending = false) ?: it }
                        if (result.data is TextMessage && currentInputText == result.data.text) {
                            currentInputText = ""
                            _effects.send(ChatSideEffect.ClearInputText)
                        }
                    }

                    is ResultWithError.Failure -> {
                        _state.update {
                            (it as? ChatUiState.Ready)?.copy(
                                isSending = false,
                                dialogError = ReadyError.SendMessageError(result.error),
                            ) ?: it
                        }
                    }
                }
            }
        }
    }

    private fun textMessage(messageId: MessageId, now: Instant, text: String): TextMessage =
        TextMessage(
            id = messageId,
            parentId = null,
            sender = currentChat!!.participants.first { it.id == currentUserId },
            recipient = chatId,
            createdAt = now,
            text = text,
        )

    fun dismissDialogError() {
        _state.update { if (it is ChatUiState.Ready) it.copy(dialogError = null) else it }
    }

    fun markMessagesAsReadUpTo(messageId: MessageId) {
        if (_state.value !is ChatUiState.Ready) return
        val unreadMessages = currentChat?.unreadMessagesCount ?: 0
        if (unreadMessages > 0) {
            viewModelScope.launch { markMessagesAsReadUseCase(chatId, messageId) }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun observeChatUpdates() {
        receiveChatUpdatesUseCase(chatId)
            .distinctUntilChanged()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                when (result) {
                    is ResultWithError.Success -> {
                        val chat = result.data
                        currentChat = chat
                        _state.value = updateUiStateFromChat(_state.value, chat)
                    }

                    is ResultWithError.Failure -> when (result.error) {
                        ReceiveChatUpdatesRepositoryError.ChatNotFound ->
                            _state.value = Error(result.error)

                        is ReceiveChatUpdatesRepositoryError.LocalOperationFailed,
                        is ReceiveChatUpdatesRepositoryError.RemoteOperationFailed,
                        -> _state.update { s ->
                            when (s) {
                                is ChatUiState.Loading -> Loading(result.error)
                                is ChatUiState.Ready -> s.copy(updateError = result.error)
                                is ChatUiState.Error -> error("Unexpected UI state Error")
                            }
                        }
                    }
                }
            }
    }

    private fun updateUiStateFromChat(state: ChatUiState, chat: Chat): ChatUiState.Ready {
        val participantUiModels = chat.participants.map { participant ->
            ParticipantUiModel(
                id = participant.id,
                name = participant.name,
                pictureUrl = participant.pictureUrl,
            )
        }.toPersistentList()

        val chatStatus = if (chat.isOneToOne) {
            val otherParticipant = chat.participants.first { it.id != currentUserId }
            ChatStatus.OneToOne(otherParticipant.onlineAt)
        } else {
            ChatStatus.Group(chat.participants.size)
        }

        val inputTextValidationError = (state as? ChatUiState.Ready?)?.inputTextValidationError
        return ChatUiState.Ready(
            id = chat.id,
            title = chat.name,
            participants = participantUiModels,
            isGroupChat = !chat.isOneToOne,
            messages = getPagedMessagesUseCase(chat.id),
            status = chatStatus,
            inputTextValidationError = inputTextValidationError,
            isSending = (state as? ChatUiState.Ready?)?.isSending == true,
            updateError = (state as? ChatUiState.Ready?)?.updateError,
            dialogError = (state as? ChatUiState.Ready?)?.dialogError,
        )
    }
}
