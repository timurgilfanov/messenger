package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
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

private const val STATE_UPDATE_DEBOUNCE = 200L

@HiltViewModel(assistedFactory = ChatViewModel.ChatViewModelFactory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted("chatId") chatIdUuid: UUID,
    @Assisted("currentUserId") currentUserIdUuid: UUID,
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveChatUpdatesUseCase: ReceiveChatUpdatesUseCase,
    private val getPagedMessagesUseCase: GetPagedMessagesUseCase,
    private val markMessagesAsReadUseCase: MarkMessagesAsReadUseCase,
) : ViewModel(),
    ContainerHost<ChatUiState, ChatSideEffect> {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val container = container<ChatUiState, ChatSideEffect>(ChatUiState.Loading()) {
        coroutineScope {
            launch { observeChatUpdates() }
        }
    }

    private var currentInputText: String = ""

    private val chatId = ChatId(chatIdUuid)
    private val currentUserId = ParticipantId(currentUserIdUuid)

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

    @OptIn(OrbitExperimental::class)
    fun onInputTextChanged(text: String) = intent {
        currentInputText = text
        runOn<ChatUiState.Ready> {
            reduce {
                val error = validateInputText(text)
                state.copy(inputTextValidationError = error)
            }
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

                val textToSend = currentInputText
                val message = textMessage(messageId, now, textToSend)
                sendMessageUseCase(currentChat!!, message).collect { result ->
                    when (result) {
                        is ResultWithError.Success -> {
                            reduce { state.copy(isSending = false) }
                            if (currentInputText == (result.data as TextMessage).text) {
                                currentInputText = ""
                                postSideEffect(ChatSideEffect.ClearInputText)
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

    @OptIn(OrbitExperimental::class)
    fun markMessagesAsReadUpTo(messageId: MessageId) = intent {
        runOn<ChatUiState.Ready> {
            val unreadMessages = currentChat?.unreadMessagesCount ?: 0
            if (unreadMessages > 0) {
                markMessagesAsReadUseCase(chatId, messageId)
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
                                    ReceiveChatUpdatesRepositoryError.ChatNotFound ->
                                        Error(result.error)
                                    is ReceiveChatUpdatesRepositoryError.LocalOperationFailed,
                                    is ReceiveChatUpdatesRepositoryError.RemoteOperationFailed,
                                    -> when (val s = state) {
                                        is ChatUiState.Loading -> Loading(result.error)

                                        is ChatUiState.Ready -> s.copy(updateError = result.error)

                                        is ChatUiState.Error -> error("Unexpected UI state Error")
                                    }
                                }
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
