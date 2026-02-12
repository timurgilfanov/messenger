package timur.gilfanov.messenger.ui.screen.chatlist

sealed interface ChatListSideEffects {
    data object Unauthorized : ChatListSideEffects
}
