package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ServerError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ServerUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.UnknownError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

private const val STATE_UPDATE_DEBOUNCE = 200L

@HiltViewModel(assistedFactory = ChatViewModel.ChatViewModelFactory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted("chatId") chatIdUuid: UUID,
    @Assisted("currentUserId") currentUserIdUuid: UUID,
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveChatUpdatesUseCase: ReceiveChatUpdatesUseCase,
) : ViewModel(),
    ContainerHost<ChatUiState, Nothing> {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val container = container<ChatUiState, Nothing>(ChatUiState.Loading()) {
        coroutineScope {
            launch { observeChatUpdates() }
        }
    }

    var observeInputTextJob: Job? = null

    private val chatId = ChatId(chatIdUuid)
    private val currentUserId = ParticipantId(currentUserIdUuid)

    @AssistedFactory
    interface ChatViewModelFactory {
        fun create(
            @Assisted("chatId") chatId: UUID,
            @Assisted("currentUserId") currentUserId: UUID,
        ): ChatViewModel
    }

    @OptIn(
        OrbitExperimental::class,
        FlowPreview::class,
        ExperimentalCoroutinesApi::class,
    )
    private suspend fun observeInputText() = subIntent {
        runOn<ChatUiState.Ready> {
            repeatOnSubscription {
                snapshotFlow { state.inputTextField.text }
                    .distinctUntilChanged()
                    .collect { inputText ->
                        ensureActive()
                        withContext(Dispatchers.Main) {
                            reduce {
                                val error = validateInputText(inputText.toString())
                                state.copy(inputTextValidationError = error)
                            }
                        }
                    }
            }
        }
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

    private var currentChat: Chat? = null

    @OptIn(OrbitExperimental::class)
    fun sendMessage(
        messageId: MessageId = MessageId(UUID.randomUUID()),
        now: Instant = Clock.System.now(),
    ) {
        intent {
            runOn<ChatUiState.Ready> {
                reduce { state.copy(isSending = true) }

                val message = textMessage(messageId, now, state.inputTextField.text.toString())
                sendMessageUseCase(currentChat!!, message).collect { result ->
                    when (result) {
                        is ResultWithError.Success -> {
                            var clear = false
                            reduce {
                                if (state.inputTextField.text ==
                                    (result.data as TextMessage).text
                                ) {
                                    clear = true
                                    state.copy(isSending = false)
                                } else {
                                    state
                                }
                            }
                            if (clear) {
                                // important to clear after the state is updated to prevent concurrency issues
                                state.inputTextField.setTextAndPlaceCursorAtEnd("")
                            }
                        }

                        is ResultWithError.Failure -> {
                            reduce {
                                state.copy(
                                    isSending = false,
                                    dialogError = ReadyError.SendMessageError(result.error),
                                )
                            }
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

    @OptIn(OrbitExperimental::class)
    fun dismissDialogError() = intent {
        runOn<ChatUiState.Ready> {
            reduce {
                state.copy(dialogError = null)
            }
        }
    }

    @OptIn(OrbitExperimental::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun observeChatUpdates() = subIntent {
        repeatOnSubscription {
            receiveChatUpdatesUseCase(chatId)
                .distinctUntilChanged()
                .debounce(STATE_UPDATE_DEBOUNCE)
                .collect { result ->
                    withContext(Dispatchers.Main) {
                        reduce {
                            when (result) {
                                is ResultWithError.Success -> {
                                    val chat = result.data
                                    currentChat = chat
                                    updateUiStateFromChat(state, chat)
                                }

                                is ResultWithError.Failure -> when (result.error) {
                                    ChatNotFound -> ChatUiState.Error(result.error)
                                    NetworkNotAvailable,
                                    ServerError,
                                    ServerUnreachable,
                                    UnknownError,
                                    -> when (val s = state) {
                                        is ChatUiState.Loading -> ChatUiState.Loading(result.error)

                                        is ChatUiState.Ready -> s.copy(updateError = result.error)

                                        is ChatUiState.Error -> error("Unexpected UI state Error")
                                    }
                                }
                            }
                        }
                    }
                    if (observeInputTextJob?.isActive != true) {
                        runOn<ChatUiState.Ready> {
                            observeInputTextJob = launch {
                                observeInputText()
                            }
                        }
                    }
                }
        }
    }

    private fun updateUiStateFromChat(state: ChatUiState, chat: Chat): ChatUiState.Ready {
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
            messages = messages,
            status = chatStatus,
            inputTextField = (state as? ChatUiState.Ready?)?.inputTextField ?: TextFieldState(""),
            inputTextValidationError = inputTextValidationError,
            isSending = (state as? ChatUiState.Ready?)?.isSending == true,
            updateError = (state as? ChatUiState.Ready?)?.updateError,
            dialogError = (state as? ChatUiState.Ready?)?.dialogError,
        )
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }
}
