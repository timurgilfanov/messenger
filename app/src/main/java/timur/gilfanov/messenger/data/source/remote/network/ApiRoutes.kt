package timur.gilfanov.messenger.data.source.remote.network

/**
 * API endpoint constants for the messenger service.
 */
object ApiRoutes {
    const val BASE_URL = "https://api.messenger.example.com/v1"

    // Chat endpoints
    const val CHATS = "/chats"
    const val CREATE_CHAT = CHATS
    const val DELETE_CHAT = "$CHATS/{chatId}"
    const val JOIN_CHAT = "$CHATS/{chatId}/join"
    const val LEAVE_CHAT = "$CHATS/{chatId}/leave"
    const val MARK_MESSAGES_AS_READ = "$CHATS/{chatId}/mark-read"

    // Message endpoints
    const val MESSAGES = "/messages"
    const val SEND_MESSAGE = MESSAGES
    const val EDIT_MESSAGE = "$MESSAGES/{messageId}"
    const val DELETE_MESSAGE = "$MESSAGES/{messageId}"

    // Sync endpoints
    const val SYNC = "/sync"
    const val CHAT_DELTAS = "$SYNC/chats/deltas"

    fun deleteChatUrl(chatId: String) = DELETE_CHAT.replace("{chatId}", chatId)
    fun joinChatUrl(chatId: String) = JOIN_CHAT.replace("{chatId}", chatId)
    fun leaveChatUrl(chatId: String) = LEAVE_CHAT.replace("{chatId}", chatId)
    fun markMessagesAsReadUrl(chatId: String) = MARK_MESSAGES_AS_READ.replace("{chatId}", chatId)
    fun editMessageUrl(messageId: String) = EDIT_MESSAGE.replace("{messageId}", messageId)
    fun deleteMessageUrl(messageId: String) = DELETE_MESSAGE.replace("{messageId}", messageId)
}
