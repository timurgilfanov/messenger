package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message

/**
 * Debug-only remote data source interface for operations that clear or reset server data.
 * These operations are used for debug data generation scenarios.
 * This interface is only available in debug builds.
 */
interface RemoteDebugDataSource {

    /**
     * Clears all server data including chats, messages, and participants.
     * Used when regenerating debug data to start with a clean server state.
     * This is a synchronous operation as it only affects in-memory fake data.
     */
    fun clearData()

    /**
     * Adds a chat directly to the server (fake implementation).
     * Used for populating the server with debug data.
     * This bypasses normal creation flows and directly updates server state.
     */
    fun addChat(chat: Chat)

    /**
     * Adds a message directly to an existing chat on the server (fake implementation).
     * Used for populating chats with debug messages.
     * This bypasses normal message sending flows and directly updates server state.
     */
    fun addMessage(message: Message)

    /**
     * Gets the current list of chats from the server (fake implementation).
     * Used for debug operations like auto-activity simulation.
     */
    fun getChats(): ImmutableList<Chat>

    fun getMessagesSize(): Int

    /**
     * Observes chat previews from the server (fake implementation).
     * Used to verify that debug data generation is reflected in the observable streams.
     */
    val chatPreviews: Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>>
}
