package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.paging.PagingData
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError

sealed interface ChatUiState {
    data class Loading(val error: ReceiveChatUpdatesError? = null) : ChatUiState
    data class Error(val error: ReceiveChatUpdatesError) : ChatUiState

    data class Ready(
        val id: ChatId,
        val title: String,
        val participants: ImmutableList<ParticipantUiModel>,
        val isGroupChat: Boolean,
        val messages: Flow<PagingData<Message>>,
        val inputTextField: TextFieldState = TextFieldState(""),
        val inputTextValidationError: TextValidationError? = null,
        val isSending: Boolean = false,
        val status: ChatStatus,
        val updateError: ReceiveChatUpdatesError? = null,
        val dialogError: ReadyError? = null,
    ) : ChatUiState
}

sealed interface ReadyError {
    data class SendMessageError(
        val error: timur.gilfanov.messenger.domain.usecase.message.SendMessageError,
    ) : ReadyError
}

data class MessageUiModel(
    val id: String,
    val text: String,
    val senderId: String,
    val senderName: String,
    val createdAt: String,
    val deliveryStatus: DeliveryStatus,
    val isFromCurrentUser: Boolean,
)

data class ParticipantUiModel(val id: ParticipantId, val name: String, val pictureUrl: String?)

sealed class ChatStatus {
    object Loading : ChatStatus()
    data class OneToOne(val otherParticipantOnlineAt: Instant?) : ChatStatus()
    data class Group(val participantCount: Int) : ChatStatus()
    data class Error(val message: String) : ChatStatus()
}
