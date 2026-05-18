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
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidator
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError as SendMessageUseCaseError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.ui.screen.chat.ChatUiState.Error
import timur.gilfanov.messenger.ui.screen.chat.ChatUiState.Loading
import timur.gilfanov.messenger.ui.screen.chat.ChatUiState.Ready
import timur.gilfanov.messenger.util.repeatOnSubscription

private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
private const val KEY_CHAT_ID = "chatId"

@Suppress("LongParameterList") // a lot of use cases is valid for ViewModel
@HiltViewModel(assistedFactory = ChatViewModel.ChatViewModelFactory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted("chatId") chatIdUuid: UUID,
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

    private data class SendRequest(val messageId: MessageId, val now: Instant, val text: String)
    private data class SendProgress(
        var acceptedLocally: Boolean = false,
        var clearInputSent: Boolean = false,
    )

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                observeChatUpdates()
            }
        }
    }

    private var currentInputText: String = ""

    private val textValidator = TextValidator(TextMessage.MAX_TEXT_LENGTH)

    private val chatId: ChatId = savedStateHandle.get<String>(KEY_CHAT_ID)?.let {
        ChatId(UUID.fromString(it))
    } ?: ChatId(chatIdUuid).also {
        savedStateHandle[KEY_CHAT_ID] = it.id.toString()
    }

    @AssistedFactory
    interface ChatViewModelFactory {
        fun create(@Assisted("chatId") chatId: UUID): ChatViewModel
    }

    private fun validateInputText(text: String): TextValidationError? =
        when (val validate = textValidator.validate(text)) {
            is ResultWithError.Failure<Unit, TextValidationError> -> validate.error
            is ResultWithError.Success<Unit, TextValidationError> -> null
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
        val value = _state.value
        if (value !is ChatUiState.Ready) return
        if (value.isSending) return

        val inputTextValidationError = validateInputText(currentInputText)
        if (inputTextValidationError != null) {
            _state.update {
                (it as? ChatUiState.Ready)?.copy(
                    inputTextValidationError = inputTextValidationError,
                ) ?: it
            }
        } else {
            val request = SendRequest(messageId, now, currentInputText)
            _state.update {
                (it as? ChatUiState.Ready)?.copy(
                    inputTextValidationError = null,
                    isSending = true,
                ) ?: it
            }
            viewModelScope.launch {
                val message = TextMessage(
                    id = request.messageId,
                    parentId = null,
                    sender = currentChat!!.participants.first { it.isCurrentUser },
                    recipient = chatId,
                    createdAt = request.now,
                    text = request.text,
                )
                val progress = SendProgress()
                sendMessageUseCase(currentChat!!, message, request.now).collect { result ->
                    when (result) {
                        is ResultWithError.Success -> handleSendSuccess(
                            result.data,
                            request,
                            progress,
                        )

                        is ResultWithError.Failure -> handleSendFailure(result.error, progress)
                    }
                }
            }
        }
    }

    private suspend fun handleSendSuccess(
        message: Message,
        request: SendRequest,
        progress: SendProgress,
    ) {
        if (!progress.acceptedLocally && message.deliveryStatus.isAcceptedStatus()) {
            progress.acceptedLocally = true
            currentChat = currentChat?.let { chat ->
                if (chat.messages.none { it.id == message.id }) {
                    chat.copy(messages = chat.messages.add(message))
                } else {
                    chat
                }
            }
            _state.update {
                (it as? Ready)?.copy(isSending = false) ?: it
            }
        }

        if (progress.acceptedLocally &&
            !progress.clearInputSent &&
            currentInputText == request.text
        ) {
            progress.clearInputSent = true
            currentInputText = ""
            _effects.send(ChatSideEffect.ClearInputText)
        }
    }

    private fun handleSendFailure(error: SendMessageUseCaseError, progress: SendProgress) {
        _state.update { state ->
            val showDialog = !progress.acceptedLocally ||
                error !is SendMessageUseCaseError.RemoteOperationFailed
            (state as? Ready)?.copy(
                isSending = if (!progress.acceptedLocally) false else state.isSending,
                dialogError = if (showDialog) {
                    ReadyError.SendMessageError(error)
                } else {
                    state.dialogError
                },
            ) ?: state
        }
    }

    /**
     * Re-sends a previously failed outgoing message, reusing its id and text.
     *
     * No-op when the chat is not [ChatUiState.Ready], when [messageId] is absent from the
     * current timeline, or when its delivery status is not [DeliveryStatus.Failed]. Only the
     * delivery status is reset (to `null`) so the send use case accepts the message; the
     * Sending/Sent/Failed transition is reflected through the message timeline rather than
     * the composer state, so this neither toggles the sending indicator nor clears the input.
     * A repeated failure follows the same dialog policy as [sendMessage] and keeps the
     * message failed and retryable.
     */
    fun retryMessage(messageId: MessageId, now: Instant = Clock.System.now()) {
        val chat = currentChat.takeIf { _state.value is ChatUiState.Ready } ?: return
        val failed = chat.messages
            .filterIsInstance<TextMessage>()
            .firstOrNull { it.id == messageId && it.deliveryStatus is DeliveryStatus.Failed }
            ?: return

        val retry = failed.copy(deliveryStatus = null)
        viewModelScope.launch {
            sendMessageUseCase(chat, retry, now).collect { result ->
                when (result) {
                    is ResultWithError.Success -> Unit
                    is ResultWithError.Failure ->
                        handleSendFailure(result.error, SendProgress(acceptedLocally = true))
                }
            }
        }
    }

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
            val otherParticipant = chat.participants.first { !it.isCurrentUser }
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
            updateError = null,
            dialogError = (state as? ChatUiState.Ready?)?.dialogError,
        )
    }
}

private fun DeliveryStatus?.isAcceptedStatus(): Boolean = when (this) {
    is DeliveryStatus.Sending,
    DeliveryStatus.Sent,
    DeliveryStatus.Delivered,
    DeliveryStatus.Read,
    -> true

    is DeliveryStatus.Failed,
    null,
    -> false
}
