package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ServerError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ServerUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.UnknownError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

class ChatViewModel(
    private val chatId: ChatId,
    private val currentUserId: ParticipantId,
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveChatUpdatesUseCase: ReceiveChatUpdatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentChat: Chat? = null

    init {
        observeChatUpdates()
    }

    fun sendMessage() {
        val messageText = (_uiState.value as ChatUiState.Ready).inputText.trim()

        viewModelScope.launch {
            _uiState.update { (it as ChatUiState.Ready).copy(isSending = true) }

            val currentParticipant = currentChat!!.participants.first { it.id == currentUserId }
            val message = createTextMessage(messageText, currentParticipant)

            sendMessageUseCase(currentChat!!, message).collect { result ->
                when (result) {
                    is ResultWithError.Success -> {
                        if (result.data.deliveryStatus == DeliveryStatus.Sent) {
                            _uiState.update {
                                (it as ChatUiState.Ready).copy(isSending = false, inputText = "")
                            }
                        }
                    }

                    is ResultWithError.Failure -> {
                        _uiState.update {
                            (it as ChatUiState.Ready).copy(
                                isSending = false,
                                dialogError = ReadyError.SendMessageError(result.error),
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { (it as ChatUiState.Ready).copy(inputText = text) }
    }

    fun dismissDialogError() {
        _uiState.update { (it as ChatUiState.Ready).copy(dialogError = null) }
    }

    private fun observeChatUpdates() {
        viewModelScope.launch {
            receiveChatUpdatesUseCase(chatId)
                .onEach { result ->
                    when (result) {
                        is ResultWithError.Success -> {
                            val chat = result.data
                            currentChat = chat
                            updateUiStateFromChat(chat)
                        }

                        is ResultWithError.Failure -> {
                            _uiState.update {
                                when (result.error) {
                                    ChatNotFound -> ChatUiState.Error(result.error)
                                    NetworkNotAvailable,
                                    ServerError,
                                    ServerUnreachable,
                                    UnknownError,
                                    -> when (it) {
                                        is ChatUiState.Loading -> ChatUiState.Loading(result.error)
                                        is ChatUiState.Ready -> it.copy(updateError = result.error)
                                        is ChatUiState.Error -> error("Unexpected UI state Error")
                                    }
                                }
                            }
                        }
                    }
                }
                .launchIn(this)
        }
    }

    private fun updateUiStateFromChat(chat: Chat) {
        val messages = chat.messages.map { message ->
            MessageUiModel(
                id = message.id.id.toString(),
                text = when (message) {
                    is TextMessage -> message.text
                    else -> error("Unsupported message type")
                },
                senderId = message.sender.id.id.toString(),
                senderName = message.sender.name,
                createdAt = formatTimestamp(message.createdAt.toEpochMilliseconds()),
                deliveryStatus = message.deliveryStatus ?: DeliveryStatus.Sending(0),
                isFromCurrentUser = message.sender.id == currentUserId,
            )
        }.toPersistentList()

        val participantUiModels = chat.participants.map { participant ->
            ParticipantUiModel(
                id = participant.id,
                name = participant.name,
                pictureUrl = participant.pictureUrl,
            )
        }.toPersistentList()

        val chatStatus = if (chat.isOneToOne) {
            val otherParticipant = chat.participants.first { it.id != currentUserId }
            ChatStatus.OneToOne("last seen recently") // TODO: Implement proper last online time
        } else {
            ChatStatus.Group(chat.participants.size)
        }

        _uiState.update {
            ChatUiState.Ready(
                id = chat.id,
                title = chat.name,
                participants = participantUiModels,
                isGroupChat = !chat.isOneToOne,
                messages = messages,
                status = chatStatus,
                inputText = (it as? ChatUiState.Ready?)?.inputText ?: "",
                isSending = (it as? ChatUiState.Ready?)?.isSending == true,
                updateError = (it as? ChatUiState.Ready?)?.updateError,
                dialogError = (it as? ChatUiState.Ready?)?.dialogError,
            )
        }
    }

    private fun createTextMessage(text: String, sender: Participant): TextMessage = TextMessage(
        id = MessageId(UUID.randomUUID()),
        parentId = null,
        sender = sender,
        recipient = chatId,
        createdAt = Clock.System.now(),
        text = text,
    )

    private fun formatTimestamp(epochMillis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }
}
