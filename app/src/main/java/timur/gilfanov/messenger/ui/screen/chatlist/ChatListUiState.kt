package timur.gilfanov.messenger.ui.screen.chatlist

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError

sealed interface ChatListUiState {
    data object Empty : ChatListUiState
    data class NotEmpty(val chats: ImmutableList<ChatListItemUiModel>) : ChatListUiState
}

data class ChatListItemUiModel(
    val id: ChatId,
    val name: String,
    val pictureUrl: String?,
    val lastMessage: String?,
    val lastMessageTime: Instant?,
    val unreadCount: Int,
    val isOnline: Boolean,
    val lastOnlineTime: Instant?,
)

data class ChatListScreenState(
    val uiState: ChatListUiState = ChatListUiState.NotEmpty(
        chats = emptyList<ChatListItemUiModel>().toPersistentList(),
    ),
    val currentUser: CurrentUserUiModel =
        CurrentUserUiModel(ParticipantId(java.util.UUID.randomUUID()), "", null),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: FlowChatListRepositoryError? = null,
)

data class CurrentUserUiModel(val id: ParticipantId, val name: String, val pictureUrl: String?)

fun Chat.toChatListItemUiModel(): ChatListItemUiModel {
    val lastMessage = messages.lastOrNull()
    val otherParticipant = if (isOneToOne && participants.size >= 2) {
        participants.drop(1).firstOrNull()
    } else {
        null
    }

    return ChatListItemUiModel(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        lastMessage = lastMessage?.let {
            when (it) {
                is TextMessage -> it.text
                else -> error("Unsupported message type for chat list: ${it::class.simpleName}")
            }
        },
        lastMessageTime = lastMessage?.createdAt,
        unreadCount = unreadMessagesCount,
        isOnline = otherParticipant?.onlineAt != null,
        lastOnlineTime = otherParticipant?.onlineAt,
    )
}

fun ChatPreview.toChatListItemUiModel(): ChatListItemUiModel {
    val otherParticipant = if (participants.size >= 2) {
        participants.drop(1).firstOrNull()
    } else {
        null
    }

    return ChatListItemUiModel(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        lastMessage = lastMessage?.let {
            when (it) {
                is TextMessage -> it.text
                else -> error("Unsupported message type for chat list: ${it::class.simpleName}")
            }
        },
        lastMessageTime = lastMessage?.createdAt,
        unreadCount = unreadMessagesCount,
        isOnline = otherParticipant?.onlineAt != null,
        lastOnlineTime = otherParticipant?.onlineAt,
    )
}
