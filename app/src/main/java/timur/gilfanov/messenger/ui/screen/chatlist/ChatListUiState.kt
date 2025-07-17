package timur.gilfanov.messenger.ui.screen.chatlist

import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

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
    val uiState: ChatListUiState = ChatListUiState.Empty,
    val currentUser: CurrentUserUiModel =
        CurrentUserUiModel(ParticipantId(java.util.UUID.randomUUID()), "", null),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
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
