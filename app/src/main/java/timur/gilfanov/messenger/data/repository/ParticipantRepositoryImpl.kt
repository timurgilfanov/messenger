package timur.gilfanov.messenger.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.data.source.local.LocalDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryEditMessageError
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositorySendMessageError

@Singleton
class ParticipantRepositoryImpl @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val remoteDataSource: RemoteDataSource,
) : ParticipantRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isUpdatingFlow = MutableStateFlow(false)

    init {
        // Start delta sync for chat list on repository creation
        repositoryScope.launch {
            performDeltaSyncLoop()
        }

        // Start background connection monitoring
        remoteDataSource.isConnected()
            .onEach { connected ->
                // Handle connection state changes - could trigger immediate sync when reconnected
            }
            .catch {
                println("ParticipantRepository: Error monitoring connection state: ${it.message}")
                it.printStackTrace()
            }
            .launchIn(repositoryScope)
    }

    private suspend fun performDeltaSyncLoop() {
        val lastSyncTimestamp = when (val result = localDataSource.getLastSyncTimestamp()) {
            is ResultWithError.Success -> result.data
            is ResultWithError.Failure -> {
                println("ParticipantRepository: Failed to get last sync timestamp: ${result.error}")
                null // Start from scratch if no timestamp available
            }
        }
        remoteDataSource.chatsDeltaUpdates(lastSyncTimestamp)
            .onEach { deltaResult ->
                if (deltaResult is ResultWithError.Success) {
                    isUpdatingFlow.value = true
                    localDataSource.applyChatListDelta(deltaResult.data)
                    isUpdatingFlow.value = false
                } else {
                    println("ParticipantRepository: Delta result was failure: $deltaResult")
                }
            }
            .catch { e ->
                println("ParticipantRepository: Error collecting chatsDeltaUpdates: ${e.message}")
                e.printStackTrace()
            }
            .launchIn(repositoryScope)
    }

    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositorySendMessageError>> {
        localDataSource.insertMessage(message)

        return remoteDataSource.sendMessage(message).map { result ->
            when (result) {
                is ResultWithError.Success -> {
                    localDataSource.updateMessage(result.data)
                    ResultWithError.Success(result.data)
                }
                is ResultWithError.Failure -> {
                    ResultWithError.Failure(mapRemoteErrorToSendMessageError(result.error))
                }
            }
        }
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositoryEditMessageError>> =
        remoteDataSource.editMessage(message).map { result ->
            when (result) {
                is ResultWithError.Success -> {
                    localDataSource.updateMessage(result.data)
                    ResultWithError.Success(result.data)
                }
                is ResultWithError.Failure -> {
                    ResultWithError.Failure(mapRemoteErrorToEditMessageError(result.error))
                }
            }
        }

    override suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListError>,
        > =
        localDataSource.flowChatList().map { localResult ->
            when (localResult) {
                is ResultWithError.Success -> ResultWithError.Success(localResult.data)
                is ResultWithError.Failure -> ResultWithError.Failure(FlowChatListError.LocalError)
            }
        }

    override fun isChatListUpdating(): Flow<Boolean> = isUpdatingFlow

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError> =
        when (val result = remoteDataSource.deleteMessage(messageId, mode)) {
            is ResultWithError.Success -> {
                localDataSource.deleteMessage(messageId, mode)
                ResultWithError.Success(Unit)
            }
            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToDeleteMessageError(result.error))
            }
        }

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        localDataSource.flowChatUpdates(chatId).map { localResult ->
            when (localResult) {
                is ResultWithError.Success -> ResultWithError.Success(localResult.data)
                is ResultWithError.Failure -> ResultWithError.Failure(ChatNotFound)
            }
        }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError> =
        when (val result = remoteDataSource.joinChat(chatId, inviteLink)) {
            is ResultWithError.Success -> {
                localDataSource.insertChat(result.data)
                ResultWithError.Success(result.data)
            }
            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToJoinChatError(result.error))
            }
        }

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryLeaveChatError> =
        when (val result = remoteDataSource.leaveChat(chatId)) {
            is ResultWithError.Success -> {
                localDataSource.deleteChat(chatId)
                ResultWithError.Success(Unit)
            }
            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToLeaveChatError(result.error))
            }
        }
}

private fun mapRemoteErrorToDeleteMessageError(
    error: RemoteDataSourceError,
): RepositoryDeleteMessageError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> RepositoryDeleteMessageError.NetworkNotAvailable
    RemoteDataSourceError.ServerUnreachable -> RepositoryDeleteMessageError.RemoteUnreachable
    RemoteDataSourceError.MessageNotFound -> RepositoryDeleteMessageError.MessageNotFound
    else -> RepositoryDeleteMessageError.RemoteError
}

private fun mapRemoteErrorToJoinChatError(error: RemoteDataSourceError): RepositoryJoinChatError =
    when (error) {
        RemoteDataSourceError.NetworkNotAvailable -> RepositoryJoinChatError.NetworkNotAvailable
        RemoteDataSourceError.ServerUnreachable -> RepositoryJoinChatError.RemoteUnreachable
        RemoteDataSourceError.ServerError -> RepositoryJoinChatError.RemoteError
        RemoteDataSourceError.ChatNotFound -> RepositoryJoinChatError.ChatNotFound
        RemoteDataSourceError.InvalidInviteLink -> RepositoryJoinChatError.InvalidInviteLink
        RemoteDataSourceError.ExpiredInviteLink -> RepositoryJoinChatError.ExpiredInviteLink
        RemoteDataSourceError.ChatClosed -> RepositoryJoinChatError.ChatClosed
        RemoteDataSourceError.AlreadyJoined -> RepositoryJoinChatError.AlreadyJoined
        RemoteDataSourceError.ChatFull -> RepositoryJoinChatError.ChatFull
        RemoteDataSourceError.UserBlocked -> RepositoryJoinChatError.UserBlocked
        is RemoteDataSourceError.CooldownActive -> RepositoryJoinChatError.CooldownActive(
            error.remaining,
        )
        else -> RepositoryJoinChatError.LocalError
    }

private fun mapRemoteErrorToLeaveChatError(
    error: RemoteDataSourceError,
): RepositoryLeaveChatError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> RepositoryLeaveChatError.NetworkNotAvailable
    RemoteDataSourceError.ServerUnreachable -> RepositoryLeaveChatError.RemoteUnreachable
    RemoteDataSourceError.ServerError -> RepositoryLeaveChatError.RemoteError
    RemoteDataSourceError.ChatNotFound -> RepositoryLeaveChatError.ChatNotFound
    else -> RepositoryLeaveChatError.LocalError
}

private fun mapRemoteErrorToSendMessageError(
    error: RemoteDataSourceError,
): RepositorySendMessageError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> RepositorySendMessageError.NetworkNotAvailable
    RemoteDataSourceError.ServerUnreachable -> RepositorySendMessageError.RemoteUnreachable
    RemoteDataSourceError.ServerError -> RepositorySendMessageError.RemoteError
    else -> RepositorySendMessageError.RemoteError
}

private fun mapRemoteErrorToEditMessageError(
    error: RemoteDataSourceError,
): RepositoryEditMessageError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> RepositoryEditMessageError.NetworkNotAvailable
    RemoteDataSourceError.ServerUnreachable -> RepositoryEditMessageError.RemoteUnreachable
    RemoteDataSourceError.ServerError -> RepositoryEditMessageError.RemoteError
    else -> RepositoryEditMessageError.RemoteError
}
