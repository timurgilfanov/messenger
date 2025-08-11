package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.paging.PagingData
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError

object ChatScreenTestFixtures {

    val defaultChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    val defaultAliceId = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
    val defaultCurrentUserId =
        ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))

    @Suppress("LongParameterList") // A lot of parameters are optional defaults
    fun createParticipant(
        id: ParticipantId = defaultAliceId,
        name: String = "Alice",
        pictureUrl: String? = null,
        joinedAt: Instant = Instant.fromEpochMilliseconds(1000),
        onlineAt: Instant? = null,
        isAdmin: Boolean = false,
        isModerator: Boolean = false,
    ) = Participant(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        joinedAt = joinedAt,
        onlineAt = onlineAt,
        isAdmin = isAdmin,
        isModerator = isModerator,
    )

    fun createParticipantUiModel(
        id: ParticipantId = defaultAliceId,
        name: String = "Alice",
        pictureUrl: String? = null,
    ) = ParticipantUiModel(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
    )

    @Suppress("LongParameterList") // A lot of parameters are optional defaults
    fun createTextMessage(
        id: MessageId = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440010")),
        parentId: MessageId? = null,
        sender: Participant,
        recipient: ChatId = defaultChatId,
        createdAt: Instant = Instant.fromEpochMilliseconds(1628000000000),
        text: String = "Hello! 👋",
        deliveryStatus: DeliveryStatus = DeliveryStatus.Read,
    ) = TextMessage(
        id = id,
        parentId = parentId,
        sender = sender,
        recipient = recipient,
        createdAt = createdAt,
        text = text,
        deliveryStatus = deliveryStatus,
    )

    @Suppress("LongParameterList") // A lot of parameters are optional defaults
    fun createChatUiStateReady(
        id: ChatId = defaultChatId,
        title: String = "Alice",
        participants: List<ParticipantUiModel> = listOf(
            createParticipantUiModel(defaultAliceId, "Alice"),
            createParticipantUiModel(defaultCurrentUserId, "You"),
        ),
        isGroupChat: Boolean = false,
        messages: List<TextMessage> = emptyList(),
        inputTextField: TextFieldState = TextFieldState(""),
        isSending: Boolean = false,
        status: ChatStatus = ChatStatus.OneToOne(null),
        inputTextValidationError: TextValidationError? = null,
        updateError: ReceiveChatUpdatesError? = null,
        dialogError: ReadyError? = null,
    ) = ChatUiState.Ready(
        id = id,
        title = title,
        participants = persistentListOf(*participants.toTypedArray()),
        isGroupChat = isGroupChat,
        messages = flowOf(PagingData.from(messages)),
        inputTextField = inputTextField,
        isSending = isSending,
        status = status,
        inputTextValidationError = inputTextValidationError,
        updateError = updateError,
        dialogError = dialogError,
    )

    // Common test scenarios
    fun createAliceAndCurrentUser(): Pair<Participant, Participant> {
        val alice = createParticipant(
            id = defaultAliceId,
            name = "Alice",
        )
        val currentUser = createParticipant(
            id = defaultCurrentUserId,
            name = "You",
        )
        return alice to currentUser
    }

    fun createSampleMessages(alice: Participant, currentUser: Participant): List<TextMessage> =
        listOf(
            createTextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440010")),
                sender = alice,
                text = "Hello! 👋",
                createdAt = Instant.fromEpochMilliseconds(1628000000000),
                deliveryStatus = DeliveryStatus.Read,
            ),
            createTextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440011")),
                sender = currentUser,
                text = "How are you doing today?",
                createdAt = Instant.fromEpochMilliseconds(1628000120000),
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        )
}
