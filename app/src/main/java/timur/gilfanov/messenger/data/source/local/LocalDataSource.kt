package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.data.source.remote.ChatDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Suppress("TooManyFunctions")
interface LocalDataSource {

    suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError>

    suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError>

    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError>

    suspend fun getChat(chatId: ChatId): ResultWithError<Chat, LocalDataSourceError>

    fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>>

    fun flowChatUpdates(chatId: ChatId): Flow<ResultWithError<Chat, LocalDataSourceError>>

    suspend fun insertMessage(message: Message): ResultWithError<Message, LocalDataSourceError>

    suspend fun updateMessage(message: Message): ResultWithError<Message, LocalDataSourceError>

    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, LocalDataSourceError>

    suspend fun getMessage(messageId: MessageId): ResultWithError<Message, LocalDataSourceError>

    suspend fun getMessagesForChat(
        chatId: ChatId,
    ): ResultWithError<List<Message>, LocalDataSourceError>

    suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError>

    suspend fun updateLastSyncTimestamp(
        timestamp: Instant,
    ): ResultWithError<Unit, LocalDataSourceError>

    suspend fun applyChatDelta(delta: ChatDelta): ResultWithError<Unit, LocalDataSourceError>

    suspend fun applyChatListDelta(
        delta: ChatListDelta,
    ): ResultWithError<Unit, LocalDataSourceError>

    suspend fun clearAllData(): ResultWithError<Unit, LocalDataSourceError>
}

sealed interface LocalDataSourceError {
    data object DatabaseUnavailable : LocalDataSourceError
    data object StorageFailure : LocalDataSourceError
    data object ChatNotFound : LocalDataSourceError
    data object MessageNotFound : LocalDataSourceError
    data object InvalidData : LocalDataSourceError
    data class UnknownError(val cause: Throwable) : LocalDataSourceError
}
