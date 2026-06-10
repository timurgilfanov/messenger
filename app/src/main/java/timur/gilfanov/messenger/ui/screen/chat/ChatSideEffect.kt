package timur.gilfanov.messenger.ui.screen.chat

sealed interface ChatSideEffect {
    sealed interface UiCommand : ChatSideEffect {
        data object InputCleared : UiCommand
    }
}
