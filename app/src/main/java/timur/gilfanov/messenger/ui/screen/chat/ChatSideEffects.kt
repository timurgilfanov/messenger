package timur.gilfanov.messenger.ui.screen.chat

sealed interface ChatSideEffects {
    sealed interface UiCommand : ChatSideEffects {
        data object ClearInput : UiCommand
    }
}
