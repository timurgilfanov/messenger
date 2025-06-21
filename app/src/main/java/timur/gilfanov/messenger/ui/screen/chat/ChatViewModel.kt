package timur.gilfanov.messenger.ui.screen.chat

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
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

@HiltViewModel(assistedFactory = ChatViewModel.ChatViewModelFactory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted("chatId") chatIdUuid: UUID,
    @Assisted("currentUserId") currentUserIdUuid: UUID,
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveChatUpdatesUseCase: ReceiveChatUpdatesUseCase,
) : ViewModel(),
    ContainerHost<ChatUiState, Nothing> {

    override val container = container<ChatUiState, Nothing>(ChatUiState.Loading()) {
        coroutineScope {
            launch { observeChatUpdates() }
        }
    }

    private val chatId = ChatId(chatIdUuid)
    private val currentUserId = ParticipantId(currentUserIdUuid)

    @AssistedFactory
    interface ChatViewModelFactory {
        fun create(
            @Assisted("chatId") chatId: UUID,
            @Assisted("currentUserId") currentUserId: UUID,
        ): ChatViewModel
    }

    private var currentChat: Chat? = null

    @OptIn(OrbitExperimental::class)
    fun sendMessage(
        messageId: MessageId = MessageId(UUID.randomUUID()),
        now: Instant = Clock.System.now(),
    ) = intent {
        runOn<ChatUiState.Ready> {
            reduce { state.copy(isSending = true) }

            val message = TextMessage(
                id = messageId,
                parentId = null,
                sender = currentChat!!.participants.first { it.id == currentUserId },
                recipient = chatId,
                createdAt = now,
                text = state.inputText.trim(),
            )

            var oneShot = true
            sendMessageUseCase(currentChat!!, message).collect { result ->
                when (result) {
                    is ResultWithError.Success -> {
                        if (oneShot) {
                            oneShot = false
                            reduce { state.copy(isSending = false, inputText = "") }
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

    @OptIn(OrbitExperimental::class)
    fun updateInputText(text: String) = intent {
        runOn<ChatUiState.Ready> {
            reduce {
                state.copy(inputText = text)
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun dismissDialogError() = intent {
        runOn<ChatUiState.Ready> {
            reduce {
                state.copy(dialogError = null)
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    private suspend fun observeChatUpdates() = subIntent {
        repeatOnSubscription {
            receiveChatUpdatesUseCase(chatId).collect { result ->
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
                            -> when (state) {
                                is ChatUiState.Loading -> ChatUiState.Loading(result.error)
                                is ChatUiState.Ready -> (state as ChatUiState.Ready).copy(
                                    updateError = result.error,
                                )

                                is ChatUiState.Error -> error("Unexpected UI state Error")
                            }
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

        return ChatUiState.Ready(
            id = chat.id,
            title = chat.name,
            participants = participantUiModels,
            isGroupChat = !chat.isOneToOne,
            messages = messages,
            status = chatStatus,
            inputText = (state as? ChatUiState.Ready?)?.inputText ?: "",
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
