package timur.gilfanov.messenger.ui.screen.chat

sealed interface ChatSideEffect {
    data object ClearInputText : ChatSideEffect
}
