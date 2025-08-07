package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Suppress("TooManyFunctions")
interface RemoteDataSource {

    suspend fun createChat(chat: Chat): ResultWithError<Chat, RemoteDataSourceError>

    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError>

    suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RemoteDataSourceError>

    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError>

    fun subscribeToChats(): Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>>

    fun chatsDeltaUpdates(
        since: Instant?,
    ): Flow<ResultWithError<ChatListDelta, RemoteDataSourceError>>

    suspend fun sendMessage(message: Message): Flow<ResultWithError<Message, RemoteDataSourceError>>

    suspend fun editMessage(message: Message): Flow<ResultWithError<Message, RemoteDataSourceError>>

    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RemoteDataSourceError>
}

sealed interface RemoteDataSourceError {
    data object NetworkNotAvailable : RemoteDataSourceError
    data object ServerUnreachable : RemoteDataSourceError
    data object ServerError : RemoteDataSourceError
    data object Unauthorized : RemoteDataSourceError
    data object ChatNotFound : RemoteDataSourceError
    data object MessageNotFound : RemoteDataSourceError
    data object InvalidInviteLink : RemoteDataSourceError
    data object ExpiredInviteLink : RemoteDataSourceError
    data object ChatClosed : RemoteDataSourceError
    data object AlreadyJoined : RemoteDataSourceError
    data object ChatFull : RemoteDataSourceError
    data object UserBlocked : RemoteDataSourceError
    data class CooldownActive(val remaining: Duration) : RemoteDataSourceError
    data object RateLimitExceeded : RemoteDataSourceError
    data class UnknownError(val cause: Throwable) : RemoteDataSourceError
}
